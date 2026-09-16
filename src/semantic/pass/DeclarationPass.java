package semantic.pass;

import generated.CParser;
import generated.CParserBaseVisitor;
import org.antlr.v4.runtime.Token;
import semantic.ErrorReporter;
import semantic.SymbolTable;
import semantic.Types;

// 声明与函数定义的语义检查：登记符号、检查重复定义和初始化类型
public class DeclarationPass {

    private final CParserBaseVisitor<String> walker;
    private final SymbolTable symbols;
    private final ErrorReporter reporter;

    public DeclarationPass(CParserBaseVisitor<String> walker, SymbolTable symbols, ErrorReporter reporter) {
        this.walker = walker;
        this.symbols = symbols;
        this.reporter = reporter;
    }

    public String functionDef(CParser.FunctionDefContext ctx) {
        String name = ctx.ID().getText();
        symbols.defineFunction(name, ctx.typeSpecifier().getText());
        symbols.enterScope();
        if (ctx.paramList() != null) {
            for (CParser.ParamContext p : ctx.paramList().param()) {
                symbols.define(p.ID().getText(), p.typeSpecifier().getText());
            }
        }
        walker.visit(ctx.compoundStmt());
        symbols.exitScope();
        return null;
    }

    public String declaration(CParser.DeclarationContext ctx) {
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
            String initType = walker.visit(ctx.expr());
            if (initType != null && !Types.isAssignable(type, initType)) {
                reporter.error(nameTok, "类型不匹配: 不能用 " + initType + " 初始化 " + type);
            }
        }
        return null;
    }
}
