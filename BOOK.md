# CompilerC 代码手册

本书逐文件介绍 CompilerC 的内容、职责和依赖关系，并给出扩展新语法特性的标准流程。

## 第一章 总览

### 1.1 流水线

```
C 源代码
  │  CLexer（ANTLR 生成）         字符流 → token 流
  ▼
  │  CParser（ANTLR 生成）         token 流 → 语法树
  ▼
  │  semantic.SemanticAnalyzer     符号表、类型检查（手写）
  ▼
  │  codegen.CodeGenerator         语法树 → LLVM IR（手写）
  ▼
LLVM IR ── clang-19 ──▶ 可执行文件
```

任一阶段失败即停止：语法错误由 ANTLR 报告，语义错误由 `ErrorReporter` 收集，全部打印后不生成 IR。

### 1.2 架构模式：门面 + pass 分包

ANTLR 的 Visitor 要求"每棵树一个遍历器类"，如果所有逻辑堆在这一个类里，文件会随着语法增长失控。因此两个阶段都采用同一模式：

- **门面类**（`SemanticAnalyzer` / `CodeGenerator`）：继承 ANTLR 的 `CParserBaseVisitor`，是唯一被 ANTLR 遍历机制调用的类。它只负责两件事：创建共享状态对象、把每个 `visitXxx` 委托给对应的 pass 类。
- **pass 类**（`semantic/pass/`、`codegen/pass/`）：按语法域拆分的普通类，各自持有共享状态的引用。它们**不直接互相调用**——遇到子树一律通过门面 `walker.visit(child)` 递归，由门面按节点类型分发。依赖关系是星型，不是网状。
- **支撑类**（`SymbolTable`、`IREmitter` 等）：不依赖 ANTLR 语法树，可独立测试。

### 1.3 依赖关系总图

```
Main ──▶ generated.CLexer / CParser ──▶ CLexer.g4 / CParser.g4
Main ──▶ semantic.SemanticAnalyzer ──▶ semantic.pass.{Expression, Declaration, Statement}Pass
                                    └─▶ semantic.{SymbolTable, Types, ErrorReporter}
Main ──▶ codegen.CodeGenerator ──────▶ codegen.pass.{Function, Declaration, Statement, Expression}Pass
                                    └─▶ codegen.{IREmitter, LLVMTypes, VariableScopes, FunctionTable, Value}
```

门面注入共享状态后，pass 类只依赖支撑类和 `walker`（门面的接口类型），`Main` 完全不感知 `pass/` 子包。

---

## 第二章 构建系统

### `Makefile`

| 目标 | 行为 |
|---|---|
| `make` / `make all` | 分两步运行 ANTLR：先 `CLexer.g4`（产出 `CLexer.tokens`），再 `CParser.g4`（用 `-lib generated` 找到 token 定义）。输出到 `generated/`，包名 `generated` |
| `make build` | 在 `all` 基础上，用 javac 编译 `src/**/*.java` 和 `generated/*.java` 到 `classes/` |
| `make run` | 编译并运行 `tests/codegen_test.c` → `.ll` → 可执行文件，打印退出码 |
| `make clean` | 删除 `generated/`、`classes/`、`tests/*.ll` 和测试二进制 |

依赖：`libs/antlr-4.13.2-complete.jar`、系统 `javac`、`clang-19`。

---

## 第三章 文法文件

### `CLexer.g4`（`lexer grammar`）

C 子集的全部 token 定义，按六组组织：关键字、标识符、常量、运算符、分隔符、空白与注释。

关键规则（也是全书反复用到的词法原则）：

- **关键字必须写在 `ID` 之前**：同长度匹配时，排在前面的规则胜出。
- **多字符运算符靠最长匹配自动优先**：`<=` 压过 `<`，无需手动排序。
- **`FloatConst` 必须在 `IntConst` 之前**，否则 `3.14` 的 `3` 会先被吃掉。
- 注释和空白用 `-> skip` 丢弃。

### `CParser.g4`（`parser grammar`）

通过 `options { tokenVocab = CLexer; }` 引用词法文件。**拆分后 parser 规则里不能再写字面量**（如 `'+'`），必须引用 token 名。

规则分三层：

- 顶层：`translationUnit`、`functionDef`、`paramList`、`param`、`declaration`、`typeSpecifier`
- 语句：`compoundStmt`、`statement`、`ifStmt`、`whileStmt`、`forStmt`、`forInit`、`returnStmt`、`breakStmt`、`exprStmt`
- 表达式：单条左递归规则 `expr`，**分支位置就是优先级**（越靠前优先级越高），`# 标签` 为每个分支生成独立的 context 类和 visit 方法

两个易错点：

1. 赋值类分支（`arrayAssign`、`assignExpr`）必须放在规则底部——它们的 RHS 只能吃到优先级更高的子表达式，放高了会把 `arr[j] = j * 2` 错解析成 `(arr[j] = j) * 2`。
2. `forStmt` 用 `init=` / `cond=` / `post=` 标签命名三个可选部分，Visitor 里直接 `ctx.cond` 访问，不要靠 `expr(0)` 猜位置。

---

## 第四章 入口

### `src/Main.java`（默认包）

流水线驱动，四个阶段依次执行，每阶段失败即停止。只依赖：

- `generated.CLexer` / `generated.CParser`（ANTLR 生成）
- `semantic.SemanticAnalyzer`（语义门面）
- `codegen.CodeGenerator`（代码生成门面）

由于门面 API 稳定，内部 pass 如何拆分 `Main` 都无感知——重构内部结构时此类零修改。

---

## 第五章 semantic 包（语义分析）

约定：语义阶段的 visit 返回值是**类型名字符串**（`"int"` / `"float"` / `"char"` / `"string"` / `"int[]"`）。

### `semantic/SemanticAnalyzer.java` —— 门面

- **职责**：创建 `SymbolTable` 和 `ErrorReporter`，构造三个 pass 并把 `this`（作为 `walker`）和共享状态注入；全部 `visitXxx` 一行委托。
- **依赖**：`generated.CParser*`、`semantic.pass.*`、`SymbolTable`、`ErrorReporter`。
- **被依赖**：`Main`；三个 pass 通过 `walker` 反向引用它递归。

### `semantic/SymbolTable.java` —— 符号表

- **职责**：变量用作用域栈（`Deque<Map<String,String>>`）管理，`enterScope`/`exitScope`/`define`/`lookup`（从内层向外查）；函数单独一张全局表（`defineFunction`/`hasFunction`/`functionReturnType`）。构造时自动开全局作用域。
- **依赖**：仅 JDK。不碰语法树，可独立单测。

### `semantic/Types.java` —— 类型规则

- **职责**：纯静态方法。`isNumeric`、`isArray`（以 `[]` 结尾）、`elementType`（`"int[]"→"int"`）、`isAssignable`（同类型，或数值类型提升为 float）。
- **依赖**：无。

### `semantic/ErrorReporter.java` —— 错误收集

- **职责**：`error(Token, msg)` 记录带行号的错误信息，`getErrors`/`hasErrors` 查询。
- **依赖**：ANTLR 的 `Token`（只为取行号）。

### `semantic/pass/ExpressionPass.java`

- **职责**：全部 15 种表达式形态的类型推导。字面量直接返回类型；`idExpr` 查符号表；二元运算检查操作数是数值并推导提升结果；`assignExpr`/`arrayAssign` 检查赋值相容；`callExpr` 查函数表；数组访问检查"确实是数组、下标是数值"。出错后返回 `"int"` 兜底，避免级联报错。
- **依赖**：`walker`、`SymbolTable`、`Types`、`ErrorReporter`。

### `semantic/pass/DeclarationPass.java`

- **职责**：`declaration`（登记符号、查重复定义、检查初始化类型；数组拒绝初始化）和 `functionDef`（登记函数签名、为参数开作用域、递归函数体）。
- **依赖**：`walker`、`SymbolTable`、`Types`、`ErrorReporter`。

### `semantic/pass/StatementPass.java`

- **职责**：只有需要管理作用域的语句——`compoundStmt`（进出块各压/弹一层作用域）、`forStmt` 和 `forInit`（init 变量单独开作用域，条件必须是数值）。`if`/`while`/`return` 只需默认递归，由门面的默认 `visitChildren` 处理，不在这里。
- **依赖**：`walker`、`SymbolTable`、`Types`、`ErrorReporter`。

---

## 第六章 codegen 包（代码生成）

约定：代码生成阶段的 visit 返回值是 `Value` 对象。

### `codegen/CodeGenerator.java` —— 门面

- **职责**：创建 `IREmitter`、`VariableScopes`、`FunctionTable`，构造四个 pass 并注入；`getIR()` 返回最终 IR 文本。
- **依赖**：`generated.CParser*`、`codegen.pass.*` 及三个支撑类。
- **被依赖**：`Main`。

### `codegen/Value.java`

- **职责**：`type`（`i32`/`float`，数组变量存元素类型）+ `ref`（`%t3`、`%a.1`、常量字面量）+ `arrayType`（数组的完整类型如 `[10 x i32]`，GEP 指令需要；非数组为 `null`）。
- **依赖**：无。

### `codegen/IREmitter.java` —— IR 输出器

- **职责**：临时变量编号（`newTemp`）、alloca 唯一命名（`allocaName`，避免跨作用域同名冲突）、标签编号（`newLabel`）、带缩进的指令输出（`emit`）、块标签输出（`emitLabel`，同时复位终结标志）、原样输出（`raw`）、**基本块终结标志**（`markTerminated`/`isTerminated`——LLVM 要求每个块以 `ret`/`br` 结尾且其后不能有指令）。
- **依赖**：无。

### `codegen/LLVMTypes.java` —— 类型映射

- **职责**：`of`（C 类型 → LLVM 类型，`int`/`char` 都是 `i32`）；`convert`（必要时插入 `sitofp`/`fptosi`）；`toI1`（把值转成 `i1` 条件：非零为真）。后两者需要 `IREmitter` 来发指令，以参数形式传入。
- **依赖**：`IREmitter`、`Value`。

### `codegen/VariableScopes.java`

- **职责**：变量名 → alloca 地址的作用域栈。结构和 `SymbolTable` 相同，但载荷是 `Value`（变量类型 + 指针名 + 数组类型）。
- **依赖**：`Value`。

### `codegen/FunctionTable.java`

- **职责**：函数名 → 返回类型 / 参数类型列表；另外记录"当前正在生成的函数的返回类型"（`FunctionPass` 在进入函数时设置，`StatementPass` 的 `return` 语句读取，用于返回值类型转换）。
- **依赖**：JDK。

### `codegen/pass/FunctionPass.java`

- **职责**：`translationUnit`（输出模块头；**预扫描所有函数签名**登记进 `FunctionTable`，允许函数互相调用；然后递归所有顶层节点）和 `functionDef`（拼 `define` 签名、开 `entry` 块、为参数 alloca+store、递归函数体、块未终结时补默认 `ret`）。
- **依赖**：`walker`、`IREmitter`、`VariableScopes`、`FunctionTable`、`LLVMTypes`。

### `codegen/pass/DeclarationPass.java`

- **职责**：`declaration`——标量 `alloca` + 可选初始化 `store`；数组 `alloca [N x T]`，`Value` 里记录 `arrayType`。
- **依赖**：`walker`、`IREmitter`、`VariableScopes`、`LLVMTypes`。

### `codegen/pass/StatementPass.java`

- **职责**：控制流的固定翻译模板。
  - `if`：`if.then` / `if.else` / `if.cont` 三个块
  - `while` / `for`：`cond` / `body` / `end` 三个块；`for` 的步进表达式生成在循环体之后、跳回条件之前
  - `break`：跳到**循环出口标签栈**的栈顶（栈是本类私有字段）
  - `return`：按 `FunctionTable.currentReturnType()` 做类型转换后 `ret`，然后 `markTerminated`
  - `compoundStmt`：开作用域；`return`/`break` 之后的死代码跳过生成
- **依赖**：`walker`、`IREmitter`、`VariableScopes`、`FunctionTable`、`LLVMTypes`。

### `codegen/pass/ExpressionPass.java`

- **职责**：每种表达式的指令模板。算术按类型选 `add`/`fadd` 等；比较用 `icmp`/`fcmp` + `zext` 转回 `i32`；`assignExpr` 查地址后 `store`；数组用 `arrayAddress` 辅助方法发 `getelementptr [N x T], ptr %arr, i32 0, i32 %idx` 后再 `load`/`store`；`postInc` 同时支持 `x++` 和 `arr[i]++`（先取地址，返回旧值）；`callExpr` 按 `FunctionTable` 的签名逐参转换类型后 `call`。
- **依赖**：`walker`、`IREmitter`、`VariableScopes`、`FunctionTable`、`LLVMTypes`、`Value`。

---

## 第七章 tests 目录

| 文件 | 覆盖点 | 期望结果 |
|---|---|---|
| `tests/test.c` | 基础声明、while、if、break、float/char 常量 | 各阶段干净通过 |
| `tests/sem_test.c` | 重复定义、未声明、块作用域失效、string 赋给 int | 恰好报 4 个带行号的语义错误 |
| `tests/codegen_test.c` | 函数调用、while、if、break（1+2+3+4 后 add(10,5)） | 退出码 15 |
| `tests/array_for_test.c` | for 循环、数组写入与读回求和 | 退出码 90 |

回归验证顺序建议：先跑两个退出码测试（验证代码生成），再跑 `sem_test.c`（验证语义错误不少报），最后 `test.c`（验证不误报）。

---

## 第八章 如何扩展一个新语法特性

以加 `continue` 为例，标准流程是：

1. **`CLexer.g4`**：`Continue : 'continue' ;` 已经有了——词法不动。
2. **`CParser.g4`**：`statement` 加 `continueStmt` 分支，新增规则 `continueStmt : Continue Semi ;`。
3. **`semantic/pass/StatementPass.java`**：加 `continueStmt` 方法（本例无语义检查，可省略，走默认递归）。
4. **`codegen/pass/StatementPass.java`**：加一个"循环条件标签栈"（类比 `breakLabels`），`continueStmt` 发 `br label %栈顶` 并 `markTerminated`。
5. **门面**：`SemanticAnalyzer` / `CodeGenerator` 各加一行委托。
6. **`Main.java`**：不动。
7. **测试**：`tests/` 加一个含 `continue` 的 `.c`，确定期望退出码后验证。

要点回顾：文法改动后必须 `make clean && make build`（ANTLR 重新生成）；表达式类改动只碰两个 `ExpressionPass`；语句类改动只碰两个 `StatementPass`——这就是 pass 分包的意义。
