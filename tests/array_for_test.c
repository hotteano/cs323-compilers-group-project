int main() {
    int arr[10];
    int sum = 0;
    for (int j = 0; j < 10; j++) {
        arr[j] = j * 2;
    }
    for (int i = 0; i < 10; i++) {
        sum = sum + arr[i];
    }
    return sum;
}
