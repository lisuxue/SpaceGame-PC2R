class PointThread extends Thread {
    Point p;
    int   increment;
    int   val;

    PointThread (Point a_p, int a_val) {
	p   = a_p;
	val = a_val;
    }


    public void run () {
	int k;

	for (k = 0; k < 50; k++) {
	    p.moveTo(val, val);
	    System.out.println("t[" + val + "] :\tavec k = " +
			       k + "\tet p = " + p);
	    if (k % 2 == 0)
		Thread.yield();
	}
    }
}