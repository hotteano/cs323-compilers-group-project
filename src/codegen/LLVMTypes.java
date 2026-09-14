package codegen;

// C 类型与 LLVM 类型之间的映射和转换指令
public final class LLVMTypes {

    private LLVMTypes() {}

    // int 和 char 都用 i32
    public static String of(String cType) {
        return switch (cType) {
            case "float" -> "float";
            case "void"  -> "void";
            default      -> "i32";
        };
    }

    // 必要时插入 sitofp / fptosi，把值转换成目标类型
    public static Value convert(IREmitter ir, Value v, String target) {
        if (v.type.equals(target)) return v;
        String t = ir.newTemp();
        if ("i32".equals(v.type) && "float".equals(target)) {
            ir.emit(t + " = sitofp i32 " + v.ref + " to float");
        } else if ("float".equals(v.type) && "i32".equals(target)) {
            ir.emit(t + " = fptosi float " + v.ref + " to i32");
        } else {
            return v;
        }
        return new Value(target, t);
    }

    // 把值转成 i1 条件（非零为真）
    public static Value toI1(IREmitter ir, Value v) {
        if ("i1".equals(v.type)) return v;
        String t = ir.newTemp();
        if ("float".equals(v.type)) {
            ir.emit(t + " = fcmp one float " + v.ref + ", 0.000000e+00");
        } else {
            ir.emit(t + " = icmp ne i32 " + v.ref + ", 0");
        }
        return new Value("i1", t);
    }
}
