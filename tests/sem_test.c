int add(int a, int b) {
    return a + b;
}

int main() {
    int a = 1;
    int a = 2;
    b = 3;
    float f = a;
    char c = 'x';
    {
        int inner = 5;
        a = inner;
    }
    inner = 6;
    a = "hello";
    f = add(a, 2) + 0.5;
    return a;
}
