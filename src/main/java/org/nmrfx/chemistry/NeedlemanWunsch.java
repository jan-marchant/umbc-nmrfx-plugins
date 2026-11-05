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
        Cell previousCell;
        double score;
        boolean isOrigin = false;
        Integer aIndex;
        Integer bIndex;
        public Cell(Integer aIndex, Integer bIndex) {
            this(aIndex,bIndex,false);
        }
        public Cell() {
            this(null,null, true);
            this.score=0;
        }
        public Cell(Integer aIndex,Integer bIndex,boolean isOrigin) {
            this.aIndex = aIndex;
            this.bIndex = bIndex;
            this.isOrigin = isOrigin;
        }
    }

    private Cell[][] M;
    private Cell[][] Ia;
    private Cell[][] Ib;
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
        Ia = new Cell[nRows][nCols];
        Ib = new Cell[nRows][nCols];
    }

    public void buildMatrix() {
        M[0][0] = new Cell();
        Ia[0][0] = new Cell();
        Ib[0][0] = new Cell();
        for (int i = 1; i < nRows; i++) {
            M[i][0] = new Cell(null,null);
            M[i][0].score = -999;
            Ia[i][0] = new Cell(null,null);
            Ia[i][0].score = -999;
            Ib[i][0] = new Cell(i-1,null);
            Ib[i][0].score = GAP_EXTENSION_SCORE * (i - 1) + GAP_INITIATION_SCORE;
            Ib[i][0].previousCell = Ib[i-1][0];
        }

        for (int j = 1; j < nCols; j++) {
            M[0][j] = new Cell(null,null);
            M[0][j].score = -999;
            Ib[0][j] = new Cell(null,null);
            Ib[0][j].score = -999;
            Ia[0][j] = new Cell(null,j-1);
            Ia[0][j].score = GAP_EXTENSION_SCORE*(j-1)+GAP_INITIATION_SCORE;
            Ia[0][j].previousCell = Ia[j-1][0];
        }

        for (int i = 1; i < nRows; i++) {
            for (int j = 1; j < nCols; j++) {
                M[i][j] = new Cell(i-1,j-1);
                Ia[i][j] = new Cell(null,j-1);
                Ib[i][j] = new Cell(i-1,null);
                double alignScore;
                if (aString.charAt(i-1) == bString.charAt(j-1)) {
                    alignScore = MATCH_SCORE;
                } else {
                    alignScore = MISMATCH_SCORE;
                }

                if (Ia[i][j-1].score+GAP_EXTENSION_SCORE > M[i][j-1].score+GAP_INITIATION_SCORE && Ia[i][j-1].score+GAP_EXTENSION_SCORE > Ib[i][j-1].score+GAP_INITIATION_SCORE) {
                    Ia[i][j].score = Ia[i][j-1].score+GAP_EXTENSION_SCORE;
                    Ia[i][j].previousCell = Ia[i][j-1];
                } else if (Ib[i][j-1].score > M[i][j-1].score) {
                    Ia[i][j].score = Ib[i][j-1].score+GAP_INITIATION_SCORE;
                    Ia[i][j].previousCell = Ib[i][j-1];
                } else {
                    Ia[i][j].score = M[i][j-1].score+GAP_INITIATION_SCORE;
                    Ia[i][j].previousCell = M[i][j-1];
                }

                if (Ib[i-1][j].score+GAP_EXTENSION_SCORE > M[i-1][j].score+GAP_INITIATION_SCORE && Ib[i-1][j].score+GAP_EXTENSION_SCORE > Ia[i-1][j].score+GAP_INITIATION_SCORE) {
                    Ib[i][j].score = Ib[i-1][j].score+GAP_EXTENSION_SCORE;
                    Ib[i][j].previousCell = Ib[i-1][j];
                } else if (Ia[i-1][j].score > M[i-1][j].score) {
                    Ib[i][j].score = Ia[i-1][j].score+GAP_INITIATION_SCORE;
                    Ib[i][j].previousCell = Ia[i-1][j];
                } else {
                    Ib[i][j].score = M[i-1][j].score+GAP_INITIATION_SCORE;
                    Ib[i][j].previousCell = M[i-1][j];
                }

                if (Ib[i-1][j-1].score > Ia[i-1][j-1].score && Ib[i-1][j-1].score > M[i-1][j-1].score) {
                    M[i][j].score = alignScore + Ib[i-1][j-1].score;
                    M[i][j].previousCell = Ib[i-1][j-1];
                } else if (Ia[i-1][j-1].score > M[i-1][j-1].score) {
                    M[i][j].score = alignScore + Ia[i-1][j-1].score;
                    M[i][j].previousCell = Ia[i-1][j-1];
                } else {
                    M[i][j].score = alignScore + M[i-1][j-1].score;
                    M[i][j].previousCell = M[i-1][j-1];
                }
            }
        }
    }

        public void processMatrix() {
            int n = nRows-1;
            int m = nCols-1;
            Cell currentCell;
            if (Ib[n][m].score > Ia[n][m].score && Ib[n][m].score > M[n][m].score) {
                currentCell = Ib[n][m];
            } else if (Ia[n][m].score > M[n][m].score) {
                currentCell = Ia[n][m];
            } else {
                currentCell = M[n][m];
            }

            while (!currentCell.isOrigin) {
                indexA.add(currentCell.aIndex);
                indexB.add(currentCell.bIndex);
                currentCell = currentCell.previousCell;
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