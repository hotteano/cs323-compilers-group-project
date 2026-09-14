parser grammar CParser;

options { tokenVocab = CLexer; }

// ── 顶层结构 ──

translationUnit : (functionDef | declaration)* EOF ;

functionDef : typeSpecifier ID LParen paramList? RParen compoundStmt ;

paramList : param (Comma param)* ;
param : typeSpecifier ID ;

declaration : typeSpecifier ID (LBracket IntConst RBracket)? (Assign expr)? Semi ;

typeSpecifier : Int | Char | Float | Void ;

// ── 语句 ──

compoundStmt : LBrace statement* RBrace ;

statement : declaration
          | ifStmt
          | whileStmt
          | forStmt
          | returnStmt
          | breakStmt
          | compoundStmt
          | exprStmt
          ;

ifStmt     : If LParen expr RParen statement (Else statement)? ;
whileStmt  : While LParen expr RParen statement ;
forStmt    : For LParen init=forInit (cond=expr)? Semi (post=expr)? RParen statement ;
forInit    : declaration | expr? Semi ;
returnStmt : Return expr? Semi ;
breakStmt  : Break Semi ;
exprStmt   : expr? Semi ;

// ── 表达式（分支位置 = 优先级，越靠前优先级越高）──

expr : ID LParen (expr (Comma expr)*)? RParen          # callExpr
     | ID LBracket expr RBracket                       # arrayAccess
     | expr PlusPlus                                   # postInc
     | expr op=(Star | Div | Mod) expr                 # mulDiv
     | expr op=(Plus | Minus) expr                     # addSub
     | expr op=(Less | LessEq | Greater | GreaterEq) expr # compare
     | expr op=(Equal | NotEqual) expr                 # equality
     | ID LBracket expr RBracket Assign expr           # arrayAssign
     | ID Assign expr                                  # assignExpr
     | ID                                              # idExpr
     | IntConst                                        # intExpr
     | FloatConst                                      # floatExpr
     | CharConst                                       # charExpr
     | StringLit                                       # stringExpr
     | LParen expr RParen                              # parenExpr
     ;
