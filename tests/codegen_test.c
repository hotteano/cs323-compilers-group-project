int add(int a, int b) {
    return a + b;
}

int main() {
    int sum = 0;
    int i = 1;
    while (i <= 10) {
        if (i == 5) {
            i++;
            break;
        }
        sum = sum + i;
        i++;
    }
    int r = add(sum, 5);
    return r;
}
