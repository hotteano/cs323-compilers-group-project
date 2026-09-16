package codegen;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 函数签名表：函数名 → 返回类型 / 参数类型列表
// 另外记录当前正在生成的函数的返回类型，供 return 语句做类型转换
public class FunctionTable {

    private final Map<String, String> retTypes = new HashMap<>();
    private final Map<String, List<String>> paramTypes = new HashMap<>();
    private String currentReturnType = "i32";

    public void define(String name, String returnType, List<String> params) {
        retTypes.put(name, returnType);
        paramTypes.put(name, params);
    }

    public String returnTypeOf(String name) {
        return retTypes.get(name);
    }

    public List<String> paramTypesOf(String name) {
        return paramTypes.get(name);
    }

    public String currentReturnType() {
        return currentReturnType;
    }

    public void setCurrentReturnType(String returnType) {
        this.currentReturnType = returnType;
    }
}
