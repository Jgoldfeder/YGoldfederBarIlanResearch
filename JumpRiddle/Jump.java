import java.io.*;
import java.util.*;

public class Jump{
    private ArrayList<Solution> solutions = new ArrayList<>();
    private int matchNumber = 0;
    
    public Jump(int matchNumber){
        this.matchNumber = matchNumber;
    }
    
    public static void main(String args[])throws Exception{       
        boolean verbose = false;
        Jump solver = new Jump(Integer.parseInt(args[0]));
        
        
        String fileName = "JumpTemp.smv";
        while(solver.matchNumber > 8){
            solver.reduceSolution().print();
        }       
        solver.stringToFile(solver.buildCode()+solver.buildLTLSolutionString(),fileName);
        solver.runNuSMV(fileName,verbose).print();
    
    }
   
    private Solution runNuSMV(String fileName,boolean verbose)throws Exception{
        return runNuSMV(fileName,verbose,matchNumber/2);
    }
    
    //runs nusmv, adding the solution it finds (or if no solution, the String "no solution") to the solution ArrayList
    private Solution runNuSMV(String fileName,boolean verbose,int numberOfSteps)throws Exception{        
        Runtime rt = Runtime.getRuntime();
        Process pr = rt.exec("nusmv -bmc -bmc_length "+numberOfSteps+" "+fileName);   
        BufferedReader input = new BufferedReader(new InputStreamReader(pr.getErrorStream()));    
        //clear error buffer
        String line=null; 
           while((line=input.readLine()) != null){
        }
        
        input = new BufferedReader(new InputStreamReader(pr.getInputStream()));    
        boolean foundSolution = false;
        Solution solution = new Solution();
        solutions.add(solution);
        
        //parse to get solution
        int state = 0;
        int direction = 0;
        int index = 0;

        
        
     
        
        while((state <= numberOfSteps) && ((line=input.readLine()) != null)){                
            if(line.startsWith("  -> State:")){
                //increment state counter
                if(state > 0){
                    //add step to solution
                    solution.addStep(direction,index);
                }
                state ++;
            }
            if(line.startsWith("    direction = ")){
                direction = Integer.parseInt(line.substring(16));
            }
            if(line.startsWith("    move = ")){
                index = Integer.parseInt(line.substring(11));
            }   
        
        }
        return solution;
    }
    
    private void stringToFile(String s,String fileName)throws Exception{
        PrintWriter writer = new PrintWriter(fileName, "UTF-8");
        writer.print(s);
        writer.close();       
    }
    
    private String buildCode()throws Exception{
        return buildCode(matchNumber/2);
    }
    
    private String buildCode(int numberOfMoves)throws Exception{ 
        return
        "MODULE main\n"+
        "VAR\n"+
        "--model consists of "+matchNumber+" potential slots for matches, which can either contain a single match, as is the initial state, contain two matches, or no matches.\n"+ 
        "--slots are 0-"+(matchNumber-1)+", augmented 3 at either end to allow overflow\n"+
        "--true signifies this match is crossed, false signifies uncrossed. Two crossed matches are represented as two consecutive slots in the array marked true\n"+
        "matches : array -3.."+(matchNumber+2)+" of boolean;\n"+
        "move : 0.."+(matchNumber-1)+";\n"+
        "direction : {-1,1};\n"+
        "DEFINE\n"+
        "--goal is all matches are crossed\n"+
        buildGoal()+            
        "ASSIGN\n"+
        buildInit()+
        buildNext();
    }
    private String readFile(String file) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader (file));
        String         line = null;
        StringBuilder  stringBuilder = new StringBuilder();
        String         ls = System.getProperty("line.separator");

        try {
            while((line = reader.readLine()) != null) {
                stringBuilder.append(line);
                stringBuilder.append(ls);
            }

            return stringBuilder.toString();
        } finally {
            reader.close();
        }
    }
      
    private String buildGoal(){
        String goal = "goal :=(TRUE";
        for(int i =0;i <  matchNumber;i++){
            goal+= " & matches["+i+"]";
        }
        goal += ");\n";  
        return goal;
    }
    private String buildInit(){
        String init = "";
        for(int i = 0;i<matchNumber;i++){
            init += "init(matches["+i+"]) :=  FALSE;\n";
        }
        return init;          
    }
    
    private String buildNext(){
        String next = "";
        for(int i = 0;i<matchNumber;i++){        
            next+=
            "next(matches["+i+"]) := case\n"+ 
            "move = "+i+" : matches["+i+" + direction];\n"+
            "move = "+i+" - direction : matches["+i+" + direction];\n"+
            "move = "+i+" - direction*2 : TRUE;\n"+
            "move = "+i+" - direction*3 : TRUE;\n"+
            "TRUE : matches["+i+"];\n"+
            "esac;\n\n"; 
        }
        return next;
    }
    
    //reduce solution from n to n-2 
    private Solution reduceSolution()throws Exception{
        String fileName = "JumpTemp.smv";
        String reductionLTLSpec = "LTLSPEC G !(matches["+(matchNumber-1)+"] & matches["+(matchNumber-2)+"]);\n";
        stringToFile(buildCode(1)+reductionLTLSpec,fileName);
        matchNumber-=2;
        return runNuSMV(fileName,false,1);       
    }
    
    private String buildLTLSolutionString(){
        return "LTLSPEC G !(goal);\n";
    }
    
    
    
    
    //class encapsulating a solution
    private class Solution{
        private ArrayList<Boolean> directionIsRight = new ArrayList<>();
        private ArrayList<Integer> index = new ArrayList<>();
        
        public void addStep(int direction,int index){
            directionIsRight.add((direction==1) ? true : false);
            this.index.add(index);
        }
        
        public void print(){
            String s = "Solution:\n";
            for(int i = 0 ; i < index.size();i++){
                s += "   move "+index.get(i)+" "+(directionIsRight.get(i) ? "right" : "left")+"\n"; 
            }
            System.out.println(s);
        }
        
        
    }
}