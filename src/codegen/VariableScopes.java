package codegen;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

// 变量名 → alloca 地址的作用域栈（Value.type 是变量类型，Value.ref 是指针名）
public class VariableScopes {

    private final Deque<Map<String, Value>> scopes = new ArrayDeque<>();

    public void enterScope() {
        scopes.push(new HashMap<>());
    }

    public void exitScope() {
        scopes.pop();
    }

    public void define(String name, Value address) {
        scopes.peek().put(name, address);
    }

    public Value lookup(String name) {
        for (Map<String, Value> scope : scopes) {
            if (scope.containsKey(name)) {
                return scope.get(name);
            }
        }
        return null;
    }
}
