package semantic;

import generated.CParser;
import generated.CParserBaseVisitor;
import java.util.List;
import org.antlr.v4.runtime.Token;

// 语义分析主类：遍历语法树，符号管理委托给 SymbolTable，类型规则委托给 Types，
// 错误收集委托给 ErrorReporter。本类只保留"遍历到什么节点时做什么"的调度逻辑。
// visit 返回值约定为表达式节点的类型名（"int"/"float"/"char"/"string"/"int[]"）。
public class SemanticAnalyzer extends CParserBaseVisitor<String> {

    private final SymbolTable symbols = new SymbolTable();
    private final ErrorReporter reporter = new ErrorReporter();

    public List<String> getErrors() {
        return reporter.getErrors();
    }

    // ── 结构节点 ──

    @Override
    public String visitFunctionDef(CParser.FunctionDefContext ctx) {
        String name = ctx.ID().getText();
        symbols.defineFunction(name, ctx.typeSpecifier().getText());
        symbols.enterScope();
        if (ctx.paramList() != null) {
            for (CParser.ParamContext p : ctx.paramList().param()) {
                symbols.define(p.ID().getText(), p.typeSpecifier().getText());
            }
        }
        visit(ctx.compoundStmt());
        symbols.exitScope();
        return null;
    }

    @Override
    public String visitCompoundStmt(CParser.CompoundStmtContext ctx) {
        symbols.enterScope();
        visitChildren(ctx);
        symbols.exitScope();
        return null;
    }

    // ── 声明 ──

    @Override
    public String visitDeclaration(CParser.DeclarationContext ctx) {
        String type = ctx.typeSpecifier().getText();
        Token nameTok = ctx.ID().getSymbol();
        String name = nameTok.getText();

        if (ctx.IntConst() != null) {
            type = type + "[]";
            if (ctx.expr() != null) {
                reporter.error(nameTok, "数组暂不支持初始化: " + name);
            }
        }

        if (symbols.isDefinedInCurrentScope(name)) {
            reporter.error(nameTok, "变量重复定义: " + name);
        } else {
            symbols.define(name, type);
        }

        if (ctx.expr() != null) {
            String initType = visit(ctx.expr());
            if (initType != null && !Types.isAssignable(type, initType)) {
                reporter.error(nameTok, "类型不匹配: 不能用 " + initType + " 初始化 " + type);
            }
        }
        return null;
    }

    // ── 表达式叶子节点 ──

    @Override
    public String visitIntExpr(CParser.IntExprContext ctx)       { return "int"; }
    @Override
    public String visitFloatExpr(CParser.FloatExprContext ctx)   { return "float"; }
    @Override
    public String visitCharExpr(CParser.CharExprContext ctx)     { return "char"; }
    @Override
    public String visitStringExpr(CParser.StringExprContext ctx) { return "string"; }

    @Override
    public String visitIdExpr(CParser.IdExprContext ctx) {
        String name = ctx.ID().getText();
        String type = symbols.lookup(name);
        if (type == null) {
            reporter.error(ctx.ID().getSymbol(), "变量未声明: " + name);
            return "int"; // 出错后假装是 int，避免级联报错
        }
        return type;
    }

    @Override
    public String visitParenExpr(CParser.ParenExprContext ctx) {
        return visit(ctx.expr());
    }

    // ── 运算节点 ──

    private String numericBinary(CParser.ExprContext left, CParser.ExprContext right, Token op) {
        String lt = visit(left);
        String rt = visit(right);
        if (!Types.isNumeric(lt) || !Types.isNumeric(rt)) {
            reporter.error(op, "运算符 " + op.getText() + " 的操作数必须是数值，实际是 " + lt + " 和 " + rt);
            return "int";
        }
        return ("float".equals(lt) || "float".equals(rt)) ? "float" : "int";
    }

    @Override
    public String visitMulDiv(CParser.MulDivContext ctx) {
        return numericBinary(ctx.expr(0), ctx.expr(1), ctx.op);
    }

    @Override
    public String visitAddSub(CParser.AddSubContext ctx) {
        return numericBinary(ctx.expr(0), ctx.expr(1), ctx.op);
    }

    @Override
    public String visitCompare(CParser.CompareContext ctx) {
        numericBinary(ctx.expr(0), ctx.expr(1), ctx.op);
        return "int";
    }

    @Override
    public String visitEquality(CParser.EqualityContext ctx) {
        numericBinary(ctx.expr(0), ctx.expr(1), ctx.op);
        return "int";
    }

    @Override
    public String visitPostInc(CParser.PostIncContext ctx) {
        return visit(ctx.expr());
    }

    @Override
    public String visitAssignExpr(CParser.AssignExprContext ctx) {
        Token nameTok = ctx.ID().getSymbol();
        String varType = symbols.lookup(nameTok.getText());
        if (varType == null) {
            reporter.error(nameTok, "变量未声明: " + nameTok.getText());
            return "int";
        }
        String exprType = visit(ctx.expr());
        if (exprType != null && !Types.isAssignable(varType, exprType)) {
            reporter.error(nameTok, "类型不匹配: 不能把 " + exprType + " 赋给 " + varType);
        }
        return varType;
    }

    @Override
    public String visitCallExpr(CParser.CallExprContext ctx) {
        String name = ctx.ID().getText();
        if (!symbols.hasFunction(name)) {
            reporter.error(ctx.ID().getSymbol(), "函数未声明: " + name);
            return "int";
        }
        for (CParser.ExprContext arg : ctx.expr()) {
            visit(arg);
        }
        return symbols.functionReturnType(name);
    }

    // ── for 循环与数组 ──

    @Override
    public String visitForStmt(CParser.ForStmtContext ctx) {
        symbols.enterScope(); // for (int i = ...; ...) 的 i 只属于循环
        if (ctx.init != null) visit(ctx.init);
        if (ctx.cond != null) {
            String condType = visit(ctx.cond);
            if (!Types.isNumeric(condType)) {
                reporter.error(ctx.cond.getStart(), "for 条件必须是数值，实际是 " + condType);
            }
        }
        if (ctx.post != null) visit(ctx.post);
        visit(ctx.statement());
        symbols.exitScope();
        return null;
    }

    @Override
    public String visitForInit(CParser.ForInitContext ctx) {
        if (ctx.declaration() != null) visit(ctx.declaration());
        else if (ctx.expr() != null) visit(ctx.expr());
        return null;
    }

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
        String indexType = visit(index);
        if (!Types.isNumeric(indexType)) {
            reporter.error(index.getStart(), "数组下标必须是数值，实际是 " + indexType);
        }
        return Types.elementType(type);
    }

    @Override
    public String visitArrayAccess(CParser.ArrayAccessContext ctx) {
        return checkArrayAccess(ctx.ID().getSymbol(), ctx.expr());
    }

    @Override
    public String visitArrayAssign(CParser.ArrayAssignContext ctx) {
        String elemType = checkArrayAccess(ctx.ID().getSymbol(), ctx.expr(0));
        String valueType = visit(ctx.expr(1));
        if (valueType != null && !Types.isAssignable(elemType, valueType)) {
            reporter.error(ctx.ID().getSymbol(),
                    "类型不匹配: 不能把 " + valueType + " 赋给 " + elemType + " 数组元素");
        }
        return elemType;
    }
}
