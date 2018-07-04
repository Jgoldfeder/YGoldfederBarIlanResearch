Jump Riddle Description:
This riddle involves n matches laid side by side, where n is an even number (an odd number trivially has no solutions). A single move consists of choosing a match which may pass two matches, either to its left or right, and be laid across the third match. Note, in subsequent moves, matches that are crossed still count seperately when moving across them. A crossed match may not be crossed twice. The goal is to cross all matches in n/2 moves.

Solver Implementation:
The java program Jump.java takes a single command line argument,  which desctibes how many matches are in the riddle it is solving. It then auto-generates a NuSMV file  that can solve that riddle, runs the file, and parses and displayes the results. The solution, if it exists, is printed as a set of direcitons, where matches are indexed from 0 to n-1 from left to right, and can be moved left or right.

Model Implementation:
The nusmv model models the riddle as a boolean array of length n, whera all values are initially false (uncrossed). When a match is moved right, the values in the two slots to its right are shifted one to the left, and the matches three to the right is replaced with the match being moved, with its value and the one imediatelly to its right set as true to signify these two matches are crossed with each other. For example:

F T T F
0 1 2  3 

when shifting match 0 over the two crossed matches (1 and 2) becomes:

T  T  T   T
0  1   2    3

because the crossed matches at 1 and 2 are shifted to 0 and 1, and the matches at 2 and 3 are set to true to signify them becoming crossed. The model uses a move counter to limit solutions to n/2 moves.

Results:
The program found that there is no solution for cases n = 4 and n = 6. For cases n=8, there is a solution. Using induction, the program can reduce an n match problem to an n-2 match problem, when n is larger than 10. Thus there is a solution for all n >=8.


Program and results by Judah Goldfeder.

