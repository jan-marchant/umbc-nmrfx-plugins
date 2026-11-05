package org.nmrfx.chemistry;

import java.util.ArrayList;
import java.util.Collections;

public class NeedlemanWunsch {
    enum Direction {
        DOWN,
        RIGHT,
        DIAG
    }
    class Cell {
        Direction bestDirection;
        double score;
    }

    private Cell[][] M;
    private final String aString;
    private final String bString;
    private final double MATCH_SCORE;
    private final double MISMATCH_SCORE;
    private final double GAP_INITIATION_SCORE;
    private final double GAP_EXTENSION_SCORE;
    private final ArrayList<Integer> indexA = new ArrayList<Integer>();
    private final ArrayList<Integer> indexB = new ArrayList<Integer>();
    int nRows;
    int nCols;
    public NeedlemanWunsch(String aString, String bString) {
        this(aString, bString, 3, -3, -2,-1);
    }

    public NeedlemanWunsch(String aString, String bString, double matchScore, double mismatchScore, double gapInitScore, double gapExtendScore) {
        this.aString = aString;
        this.bString = bString;
        this.MATCH_SCORE = matchScore;
        this.MISMATCH_SCORE = mismatchScore;
        this.GAP_INITIATION_SCORE = gapInitScore;
        this.GAP_EXTENSION_SCORE = gapExtendScore;
        this.nRows = aString.length()+1;
        this.nCols = bString.length()+1;
        M = new Cell[nRows][nCols];
    }

    public void buildMatrix() {
        for (int i = 0; i < nRows; i++) {
            M[i][0] = new Cell();
            M[i][0].score = GAP_EXTENSION_SCORE*(i-1)+GAP_INITIATION_SCORE;
            M[i][0].bestDirection = Direction.RIGHT;
        }

        for (int j = 0; j < nCols; j++) {
            M[0][j] = new Cell();
            M[0][j].score = GAP_EXTENSION_SCORE*(j-1)+GAP_INITIATION_SCORE;
            M[0][j].bestDirection = Direction.DOWN;
        }

        for (int i = 1; i < nRows; i++) {
            for (int j = 1; j < nCols; j++) {
                M[i][j] = new Cell();
                double alignScore;
                if (aString.charAt(i-1) == bString.charAt(j-1)) {
                    alignScore = MATCH_SCORE;
                } else {
                    alignScore = MISMATCH_SCORE;
                }
                double downScore = M[i][j-1].score + (M[i][j-1].bestDirection == Direction.DOWN ? GAP_EXTENSION_SCORE:GAP_INITIATION_SCORE);
                double rightScore = M[i-1][j].score + (M[i-1][j].bestDirection == Direction.RIGHT ? GAP_EXTENSION_SCORE:GAP_INITIATION_SCORE);
                double diagScore = M[i-1][j-1].score + alignScore;
                if (downScore > rightScore && downScore > diagScore) {
                    M[i][j].score = downScore;
                    M[i][j].bestDirection = Direction.DOWN;
                } else if (rightScore > diagScore) {
                    M[i][j].score = rightScore;
                    M[i][j].bestDirection = Direction.RIGHT;
                } else {
                    M[i][j].score = diagScore;
                    M[i][j].bestDirection = Direction.DIAG;
                }
            }
        }
    }

    public void processMatrix() {
        int n = nRows-1;
        int m = nCols-1;
        while (n > 0 || m > 0) {
            switch (M[n][m].bestDirection) {
                case Direction.DIAG:
                    indexA.add(n-1);
                    indexB.add(m-1);
                    n--;
                    m--;
                    break;
                case Direction.DOWN:
                    indexA.add(null);
                    indexB.add(m-1);
                    m--;
                    break;
                case Direction.RIGHT:
                    indexA.add(n-1);
                    indexB.add(null);
                    n--;
                    break;
            }
        }
        Collections.reverse(indexA);
        Collections.reverse(indexB);
    }

    public ArrayList<Integer> getA() {
        return indexA;
    }

    public ArrayList<Integer> getB() {
        return indexB;
    }
}