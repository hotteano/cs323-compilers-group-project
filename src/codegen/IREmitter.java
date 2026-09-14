package codegen;

// LLVM IR 输出器：管临时变量编号、标签编号、缩进输出和"当前基本块是否已终结"标志
public class IREmitter {

    private final StringBuilder out = new StringBuilder();
    private int tempCount = 0;
    private int labelCount = 0;
    private boolean terminated = false;

    public String newTemp() {
        return "%t" + (tempCount++);
    }

    // alloca 的名字带唯一后缀，避免不同作用域的同名变量冲突
    public String allocaName(String varName) {
        return "%" + varName + "." + (tempCount++);
    }

    public String newLabel(String base) {
        return base + "." + (labelCount++);
    }

    // 输出一条带缩进的指令
    public void emit(String instruction) {
        out.append("  ").append(instruction).append('\n');
    }

    // 输出基本块标签；新块开启后终结标志复位
    public void emitLabel(String label) {
        out.append(label).append(":\n");
        terminated = false;
    }

    // 原样输出（函数签名、模块头等不需要缩进的内容）
    public void raw(String text) {
        out.append(text);
    }

    // ret / br 之后调用，标记当前块已终结
    public void markTerminated() {
        terminated = true;
    }

    public boolean isTerminated() {
        return terminated;
    }

    public String getIR() {
        return out.toString();
    }
}
