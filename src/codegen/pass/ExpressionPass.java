package codegen.pass;

import codegen.FunctionTable;
import codegen.IREmitter;
import codegen.LLVMTypes;
import codegen.Value;
import codegen.VariableScopes;
import generated.CParser;
import generated.CParserBaseVisitor;
import java.util.List;
import org.antlr.v4.runtime.Token;

// 表达式的代码生成：每种表达式翻译成对应的 LLVM 指令，返回结果值（类型 + IR 引用）
public class ExpressionPass {

    private final CParserBaseVisitor<Value> walker;
    private final IREmitter ir;
    private final VariableScopes vars;
    private final FunctionTable functions;

    public ExpressionPass(CParserBaseVisitor<Value> walker, IREmitter ir,
                          VariableScopes vars, FunctionTable functions) {
        this.walker = walker;
        this.ir = ir;
        this.vars = vars;
        this.functions = functions;
    }

    // ── 叶子节点 ──

    public Value intExpr(CParser.IntExprContext ctx) {
        return new Value("i32", ctx.getText());
    }

    public Value floatExpr(CParser.FloatExprContext ctx) {
        double d = Double.parseDouble(ctx.getText());
        return new Value("float", String.format("%e", d));
    }

    public Value charExpr(CParser.CharExprContext ctx) {
        String inner = ctx.getText().substring(1, ctx.getText().length() - 1);
        int code;
        if (inner.charAt(0) != '\\') {
            code = inner.charAt(0);
        } else {
            code = switch (inner.charAt(1)) {
                case 'n' -> '\n';
                case 't' -> '\t';
                case '0' -> 0;
                default  -> inner.charAt(1);
            };
        }
        return new Value("i32", String.valueOf(code));
    }

    public Value idExpr(CParser.IdExprContext ctx) {
        Value var = vars.lookup(ctx.ID().getText());
        String t = ir.newTemp();
        ir.emit(t + " = load " + var.type + ", ptr " + var.ref);
        return new Value(var.type, t);
    }

    public Value parenExpr(CParser.ParenExprContext ctx) {
        return walker.visit(ctx.expr());
    }

    // ── 算术与比较 ──

    private Value binary(CParser.ExprContext left, CParser.ExprContext right, String op) {
        Value l = walker.visit(left);
        Value r = walker.visit(right);
        boolean isFloat = "float".equals(l.type) || "float".equals(r.type);
        String type = isFloat ? "float" : "i32";
        l = LLVMTypes.convert(ir, l, type);
        r = LLVMTypes.convert(ir, r, type);

        String inst = switch (op) {
            case "+" -> isFloat ? "fadd" : "add";
            case "-" -> isFloat ? "fsub" : "sub";
            case "*" -> isFloat ? "fmul" : "mul";
            case "/" -> isFloat ? "fdiv" : "sdiv";
            case "%" -> isFloat ? "frem" : "srem";
            default -> throw new IllegalStateException("未知运算符: " + op);
        };
        String t = ir.newTemp();
        ir.emit(t + " = " + inst + " " + type + " " + l.ref + ", " + r.ref);
        return new Value(type, t);
    }

    private Value comparison(CParser.ExprContext left, CParser.ExprContext right, String op) {
        Value l = walker.visit(left);
        Value r = walker.visit(right);
        boolean isFloat = "float".equals(l.type) || "float".equals(r.type);
        String type = isFloat ? "float" : "i32";
        l = LLVMTypes.convert(ir, l, type);
        r = LLVMTypes.convert(ir, r, type);

        String pred = switch (op) {
            case "<"  -> isFloat ? "olt" : "slt";
            case "<=" -> isFloat ? "ole" : "sle";
            case ">"  -> isFloat ? "ogt" : "sgt";
            case ">=" -> isFloat ? "oge" : "sge";
            case "==" -> isFloat ? "oeq" : "eq";
            case "!=" -> isFloat ? "one" : "ne";
            default -> throw new IllegalStateException("未知比较: " + op);
        };
        String t = ir.newTemp();
        ir.emit(t + " = " + (isFloat ? "fcmp " : "icmp ") + pred + " " + type + " " + l.ref + ", " + r.ref);
        String z = ir.newTemp();
        ir.emit(z + " = zext i1 " + t + " to i32");
        return new Value("i32", z);
    }

    public Value addSub(CParser.AddSubContext ctx) {
        return binary(ctx.expr(0), ctx.expr(1), ctx.op.getText());
    }

    public Value mulDiv(CParser.MulDivContext ctx) {
        return binary(ctx.expr(0), ctx.expr(1), ctx.op.getText());
    }

    public Value compare(CParser.CompareContext ctx) {
        return comparison(ctx.expr(0), ctx.expr(1), ctx.op.getText());
    }

    public Value equality(CParser.EqualityContext ctx) {
        return comparison(ctx.expr(0), ctx.expr(1), ctx.op.getText());
    }

    // ── 赋值与数组 ──

    public Value assignExpr(CParser.AssignExprContext ctx) {
        Value var = vars.lookup(ctx.ID().getText());
        Value v = LLVMTypes.convert(ir, walker.visit(ctx.expr()), var.type);
        ir.emit("store " + var.type + " " + v.ref + ", ptr " + var.ref);
        return v;
    }

    // 计算数组元素的地址：getelementptr [N x T], ptr %arr, i32 0, i32 %idx
    private Value arrayAddress(Token nameTok, CParser.ExprContext indexExpr) {
        Value var = vars.lookup(nameTok.getText());
        Value idx = LLVMTypes.convert(ir, walker.visit(indexExpr), "i32");
        String p = ir.newTemp();
        ir.emit(p + " = getelementptr " + var.arrayType + ", ptr " + var.ref + ", i32 0, i32 " + idx.ref);
        return new Value(var.type, p);
    }

    public Value arrayAccess(CParser.ArrayAccessContext ctx) {
        Value addr = arrayAddress(ctx.ID().getSymbol(), ctx.expr());
        String t = ir.newTemp();
        ir.emit(t + " = load " + addr.type + ", ptr " + addr.ref);
        return new Value(addr.type, t);
    }

    public Value arrayAssign(CParser.ArrayAssignContext ctx) {
        Value addr = arrayAddress(ctx.ID().getSymbol(), ctx.expr(0));
        Value v = LLVMTypes.convert(ir, walker.visit(ctx.expr(1)), addr.type);
        ir.emit("store " + addr.type + " " + v.ref + ", ptr " + addr.ref);
        return v;
    }

    public Value postInc(CParser.PostIncContext ctx) {
        // 支持 x++ 和 arr[i]++，先取地址
        Value addr;
        if (ctx.expr() instanceof CParser.IdExprContext id) {
            Value var = vars.lookup(id.ID().getText());
            addr = new Value(var.type, var.ref);
        } else if (ctx.expr() instanceof CParser.ArrayAccessContext arr) {
            addr = arrayAddress(arr.ID().getSymbol(), arr.expr());
        } else {
            throw new RuntimeException("++ 只支持变量或数组元素");
        }
        String oldV = ir.newTemp();
        ir.emit(oldV + " = load " + addr.type + ", ptr " + addr.ref);
        String newV = ir.newTemp();
        ir.emit(newV + " = add " + addr.type + " " + oldV + ", 1");
        ir.emit("store " + addr.type + " " + newV + ", ptr " + addr.ref);
        return new Value(addr.type, oldV); // 后缀 ++ 返回旧值
    }

    // ── 函数调用 ──

    public Value callExpr(CParser.CallExprContext ctx) {
        String name = ctx.ID().getText();
        String retType = functions.returnTypeOf(name);
        List<String> paramTypes = functions.paramTypesOf(name);

        StringBuilder args = new StringBuilder();
        int i = 0;
        for (CParser.ExprContext e : ctx.expr()) {
            Value v = LLVMTypes.convert(ir, walker.visit(e), paramTypes.get(i));
            if (i > 0) args.append(", ");
            args.append(paramTypes.get(i)).append(" ").append(v.ref);
            i++;
        }
        String t = ir.newTemp();
        ir.emit(t + " = call " + retType + " @" + name + "(" + args + ")");
        return new Value(retType, t);
    }
}
