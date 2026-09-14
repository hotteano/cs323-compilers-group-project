package semantic;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

// 符号表：变量用作用域栈管理，函数单独一张全局表。类型用字符串表示（"int"、"int[]"）
public class SymbolTable {

    private final Deque<Map<String, String>> scopes = new ArrayDeque<>();
    private final Map<String, String> functions = new HashMap<>();

    public SymbolTable() {
        enterScope(); // 全局作用域
    }

    public void enterScope() {
        scopes.push(new HashMap<>());
    }

    public void exitScope() {
        scopes.pop();
    }

    public boolean isDefinedInCurrentScope(String name) {
        return scopes.peek().containsKey(name);
    }

    public void define(String name, String type) {
        scopes.peek().put(name, type);
    }

    // 从内层作用域向外层查找
    public String lookup(String name) {
        for (Map<String, String> scope : scopes) {
            if (scope.containsKey(name)) {
                return scope.get(name);
            }
        }
        return null;
    }

    public void defineFunction(String name, String returnType) {
        functions.put(name, returnType);
    }

    public boolean hasFunction(String name) {
        return functions.containsKey(name);
    }

    public String functionReturnType(String name) {
        return functions.get(name);
    }
}
