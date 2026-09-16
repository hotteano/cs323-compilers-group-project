package semantic.pass;

import generated.CParser;
import generated.CParserBaseVisitor;
import org.antlr.v4.runtime.Token;
import semantic.ErrorReporter;
import semantic.SymbolTable;
import semantic.Types;

// 表达式的语义检查：推导并返回每个表达式的类型名（"int"/"float"/"char"/"string"）
// walker 是门面 SemanticAnalyzer，递归访问子表达式一律经过它分发
public class ExpressionPass {

    private final CParserBaseVisitor<String> walker;
    private final SymbolTable symbols;
    private final ErrorReporter reporter;

    public ExpressionPass(CParserBaseVisitor<String> walker, SymbolTable symbols, ErrorReporter reporter) {
        this.walker = walker;
        this.symbols = symbols;
        this.reporter = reporter;
    }

    // ── 叶子节点 ──

    public String intExpr(CParser.IntExprContext ctx)       { return "int"; }
    public String floatExpr(CParser.FloatExprContext ctx)   { return "float"; }
    public String charExpr(CParser.CharExprContext ctx)     { return "char"; }
    public String stringExpr(CParser.StringExprContext ctx) { return "string"; }

    public String idExpr(CParser.IdExprContext ctx) {
        String name = ctx.ID().getText();
        String type = symbols.lookup(name);
        if (type == null) {
            reporter.error(ctx.ID().getSymbol(), "变量未声明: " + name);
            return "int"; // 出错后假装是 int，避免级联报错
        }
        return type;
    }

    public String parenExpr(CParser.ParenExprContext ctx) {
        return walker.visit(ctx.expr());
    }

    // ── 二元运算 ──

    private String numericBinary(CParser.ExprContext left, CParser.ExprContext right, Token op) {
        String lt = walker.visit(left);
        String rt = walker.visit(right);
        if (!Types.isNumeric(lt) || !Types.isNumeric(rt)) {
            reporter.error(op, "运算符 " + op.getText() + " 的操作数必须是数值，实际是 " + lt + " 和 " + rt);
            return "int";
        }
        return ("float".equals(lt) || "float".equals(rt)) ? "float" : "int";
    }

    public String mulDiv(CParser.MulDivContext ctx) {
        return numericBinary(ctx.expr(0), ctx.expr(1), ctx.op);
    }

    public String addSub(CParser.AddSubContext ctx) {
        return numericBinary(ctx.expr(0), ctx.expr(1), ctx.op);
    }

    public String compare(CParser.CompareContext ctx) {
        numericBinary(ctx.expr(0), ctx.expr(1), ctx.op);
        return "int"; // 比较结果在 C 中是 int
    }

    public String equality(CParser.EqualityContext ctx) {
        numericBinary(ctx.expr(0), ctx.expr(1), ctx.op);
        return "int";
    }

    public String postInc(CParser.PostIncContext ctx) {
        return walker.visit(ctx.expr());
    }

    // ── 赋值与调用 ──

    public String assignExpr(CParser.AssignExprContext ctx) {
        Token nameTok = ctx.ID().getSymbol();
        String varType = symbols.lookup(nameTok.getText());
        if (varType == null) {
            reporter.error(nameTok, "变量未声明: " + nameTok.getText());
            return "int";
        }
        String exprType = walker.visit(ctx.expr());
        if (exprType != null && !Types.isAssignable(varType, exprType)) {
            reporter.error(nameTok, "类型不匹配: 不能把 " + exprType + " 赋给 " + varType);
        }
        return varType;
    }

    public String callExpr(CParser.CallExprContext ctx) {
        String name = ctx.ID().getText();
        if (!symbols.hasFunction(name)) {
            reporter.error(ctx.ID().getSymbol(), "函数未声明: " + name);
            return "int";
        }
        for (CParser.ExprContext arg : ctx.expr()) {
            walker.visit(arg);
        }
        return symbols.functionReturnType(name);
    }

    // ── 数组 ──

    // 检查"名字确实是数组、下标是数值"，返回元素类型
    private String checkArrayAccess(Token nameTok, CParser.ExprContext index) {
        String type = symbols.lookup(nameTok.getText());
        if (type == null) {
            reporter.error(nameTok, "变量未声明: " + nameTok.getText());
            return "int";
        }
        if (!Types.isArray(type)) {
            reporter.error(nameTok, "不是数组，不能下标访问: " + nameTok.getText());
            return "int";
        }
        String indexType = walker.visit(index);
        if (!Types.isNumeric(indexType)) {
            reporter.error(index.getStart(), "数组下标必须是数值，实际是 " + indexType);
        }
        return Types.elementType(type);
    }

    public String arrayAccess(CParser.ArrayAccessContext ctx) {
        return checkArrayAccess(ctx.ID().getSymbol(), ctx.expr());
    }

    public String arrayAssign(CParser.ArrayAssignContext ctx) {
        String elemType = checkArrayAccess(ctx.ID().getSymbol(), ctx.expr(0));
        String valueType = walker.visit(ctx.expr(1));
        if (valueType != null && !Types.isAssignable(elemType, valueType)) {
            reporter.error(ctx.ID().getSymbol(),
                    "类型不匹配: 不能把 " + valueType + " 赋给 " + elemType + " 数组元素");
        }
        return elemType;
    }
}
