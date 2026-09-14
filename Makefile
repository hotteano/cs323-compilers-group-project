ANTLR_JAR  := libs/antlr-4.13.2-complete.jar
GEN_DIR    := generated
SRC_DIR    := src

all: $(GEN_DIR)/CParser.java

# 先生成词法器（产出 CLexer.tokens），parser 生成时用 -lib 找到它
$(GEN_DIR)/CParser.java: CLexer.g4 CParser.g4
	mkdir -p $(GEN_DIR)
	java -jar $(ANTLR_JAR) -visitor -o $(GEN_DIR) -package generated CLexer.g4
	java -jar $(ANTLR_JAR) -visitor -o $(GEN_DIR) -lib $(GEN_DIR) -package generated CParser.g4

build: all
	mkdir -p classes
	javac -cp $(ANTLR_JAR) -d classes $(shell find $(SRC_DIR) -name '*.java') $(GEN_DIR)/*.java

run: build
	java -cp classes:$(ANTLR_JAR) Main tests/codegen_test.c
	clang-19 tests/codegen_test.ll -o tests/codegen_test
	./tests/codegen_test; echo "退出码: $$?"

clean:
	rm -rf $(GEN_DIR) classes tests/*.ll tests/codegen_test tests/array_for_test

.PHONY: all build run clean
