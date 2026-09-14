# cs323-compilers-group-project

一个 C 语言子集的教学编译器，覆盖编译器前端的完整流水线：

```
C 源代码 → 词法分析 → 语法分析 → 语义分析 → LLVM IR → 可执行文件
           (ANTLR)    (ANTLR)    (手写)     (手写)    (clang)
```

## 功能

- **词法 / 语法分析**：ANTLR4 生成，文法拆分为 `CLexer.g4`（词法）和 `CParser.g4`（语法）
- **语义分析**：作用域管理（块级作用域栈）、变量先声明后使用检查、重复定义检查、基本类型检查（int / char / float、数组）
- **代码生成**：生成 LLVM IR，支持：
  - 函数定义与调用（含参数、递归）
  - 局部变量、数组（`int arr[10]`、下标读写、`arr[i]++`）
  - 算术 / 比较运算，int 与 float 混合运算自动提升
  - `if` / `else`、`while`、`for`、`break`、`return`
- **错误报告**：语法错误和语义错误均带行号

## 目录结构

```
├── CLexer.g4 / CParser.g4   ANTLR 文法
├── Makefile                 构建入口
├── libs/                    ANTLR 4.13.2 运行时
├── src/
│   ├── Main.java            流水线驱动
│   ├── semantic/            语义分析（符号表、类型规则、错误收集）
│   └── codegen/             代码生成（IR 输出、类型映射、作用域）
└── tests/                   测试输入（.c 文件）
```

## 环境要求

- JDK 21+
- LLVM / Clang 19（`clang-19`）
- GNU Make

## 使用

```bash
# 一键编译并运行示例（tests/codegen_test.c，用退出码验证结果）
make run

# 只生成 ANTLR 代码并编译 Java
make build

# 编译任意 C 源文件，生成同路径的 .ll
java -cp classes:libs/antlr-4.13.2-complete.jar Main tests/array_for_test.c
clang-19 tests/array_for_test.ll -o tests/array_for_test
./tests/array_for_test; echo $?

# 清理所有生成物
make clean
```

## 测试

| 测试文件 | 验证内容 | 期望结果 |
|---|---|---|
| `tests/codegen_test.c` | 函数调用、while、break、if | 退出码 15 |
| `tests/array_for_test.c` | for 循环、数组读写 | 退出码 90 |
| `tests/sem_test.c` | 语义错误检查 | 报 4 个带行号的语义错误 |
| `tests/test.c` | 基础词法/语法/类型 | 干净通过 |

## 路线图

- [ ] `continue` 语句
- [ ] 数组作为函数参数（数组退化为指针）
- [ ] 二维数组
- [ ] `printf` 等内建函数
