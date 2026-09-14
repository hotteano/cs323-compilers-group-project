lexer grammar CLexer;

// ── 1. 关键字（必须写在 ID 前面，靠规则顺序优先）──
Int      : 'int' ;
Char     : 'char' ;
Float    : 'float' ;
Void     : 'void' ;
If       : 'if' ;
Else     : 'else' ;
While    : 'while' ;
For      : 'for' ;
Return   : 'return' ;
Break    : 'break' ;
Continue : 'continue' ;

// ── 2. 标识符（字母/下划线开头，后跟字母数字下划线）──
ID : [a-zA-Z_][a-zA-Z_0-9]* ;

// ── 3. 常量 ──
FloatConst : [0-9]+ '.' [0-9]* ([eE] [+-]? [0-9]+)? ;   // 3.14, 1e5
IntConst   : [0-9]+ ;                                    // 必须写在 Float 后面
CharConst  : '\'' (~['\\] | '\\' .) '\'' ;               // 'a', '\n'
StringLit  : '"' (~["\\] | '\\' .)* '"' ;                // "hello\n"

// ── 4. 运算符（多字符的靠最长匹配自动优先）──
PlusPlus : '++' ;  MinusMinus : '--' ;
Equal    : '==' ;  NotEqual   : '!=' ;
LessEq   : '<=' ;  GreaterEq  : '>=' ;
And      : '&&' ;  Or         : '||' ;
PlusAssign : '+=' ; MinusAssign : '-=' ;

Plus  : '+' ;  Minus : '-' ;  Star : '*' ;  Div : '/' ;  Mod : '%' ;
Assign : '=' ; Less : '<' ;  Greater : '>' ; Not : '!' ;
BitAnd : '&' ; BitOr : '|' ;

// ── 5. 分隔符 ──
LParen : '(' ;  RParen : ')' ;
LBrace : '{' ;  RBrace : '}' ;
LBracket : '[' ; RBracket : ']' ;
Semi   : ';' ;  Comma  : ',' ;

// ── 6. 空白和注释（直接丢弃）──
WS           : [ \t\r\n]+ -> skip ;
LineComment  : '//' ~[\r\n]* -> skip ;
BlockComment : '/*' .*? '*/' -> skip ;
