package semantic;

// C 子集的类型规则，类型一律用字符串表示
public final class Types {

    private Types() {}

    public static boolean isNumeric(String type) {
        return "int".equals(type) || "float".equals(type) || "char".equals(type);
    }

    public static boolean isArray(String type) {
        return type != null && type.endsWith("[]");
    }

    // "int[]" -> "int"
    public static String elementType(String arrayType) {
        return arrayType.substring(0, arrayType.length() - 2);
    }

    // int/char 可提升为 float，其余要求类型相同
    public static boolean isAssignable(String target, String source) {
        if (target.equals(source)) return true;
        return "float".equals(target) && isNumeric(source);
    }
}
