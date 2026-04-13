public class test {

    static int add(int a, int b) {
        return a + b;
    }

    public static void main(String[] args) {

        int x = 10;
        int y = 20;
        int z = 0;

        z = x + y;

        boolean flag = (x < y) && (z != 0);

        if (flag) {
            System.out.println("flag is true");
        } else {
            System.out.println("flag is false");
        }

        for (int i = 0; i < 5; i++) {
            int sq = i * i;
            System.out.println(sq);
        }

        int count = 0;
        while (count < 3) {
            count++;
        }

        int result = add(x, y);
        System.out.println(result);

    }

}
