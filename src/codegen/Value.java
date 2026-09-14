package codegen;

// 代码生成中 visit 的返回值：一个 LLVM 值 = 类型 + IR 引用
public class Value {

    public final String type;      // i32 / float（数组变量这里存元素类型）
    public final String ref;       // %t3、%a.1、42、3.140000e+00
    public final String arrayType; // 数组的完整类型，如 [10 x i32]；非数组为 null

    public Value(String type, String ref) {
        this(type, ref, null);
    }

    public Value(String type, String ref, String arrayType) {
        this.type = type;
        this.ref = ref;
        this.arrayType = arrayType;
    }
}
