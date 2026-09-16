package codegen.pass;

import codegen.IREmitter;
import codegen.LLVMTypes;
import codegen.Value;
import codegen.VariableScopes;
import generated.CParser;
import generated.CParserBaseVisitor;

// 声明的代码生成：标量 alloca（可带初始化）、数组 alloca [N x T]
public class DeclarationPass {

    private final CParserBaseVisitor<Value> walker;
    private final IREmitter ir;
    private final VariableScopes vars;

    public DeclarationPass(CParserBaseVisitor<Value> walker, IREmitter ir, VariableScopes vars) {
        this.walker = walker;
        this.ir = ir;
        this.vars = vars;
    }

    public Value declaration(CParser.DeclarationContext ctx) {
        String type = LLVMTypes.of(ctx.typeSpecifier().getText());
        String name = ctx.ID().getText();
        String addr = ir.allocaName(name);

        if (ctx.IntConst() != null) {
            // 数组：分配一整块 [N x 元素类型]
            String arrayType = "[" + ctx.IntConst().getText() + " x " + type + "]";
            ir.emit(addr + " = alloca " + arrayType);
            vars.define(name, new Value(type, addr, arrayType));
            return null;
        }

        ir.emit(addr + " = alloca " + type);
        vars.define(name, new Value(type, addr));

        if (ctx.expr() != null) {
            Value v = LLVMTypes.convert(ir, walker.visit(ctx.expr()), type);
            ir.emit("store " + type + " " + v.ref + ", ptr " + addr);
        }
        return null;
    }
}
