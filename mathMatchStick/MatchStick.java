import java.io.*;
import java.util.*;

public class MatchStick{
    private ArrayList<String> solutions = new ArrayList<>();
    private ArrayList<String> restrictions = new ArrayList<>();
    //every numeric arg must be seperated by an operator arg. First and last args must be numeric
    private ArrayList<String> numArgs = new ArrayList<>();
    private ArrayList<String> opArgs = new ArrayList<>();
    
    private int numberOfMoves = -1;//this default value signifies no limit 
    private int offset = 0;//signifies the solution may add/remove matches
    private static final String info = "Wrong format. Arguments must be entered in the form: 'A + B = C' or 'A - B = C'. \n Add -v parameter to see the unparsed NuSMV printout. \n     >Note this will only include the printout from the first solution. \n Add -m parameter followed by a number to limit how many matches may be moved. \n Add -o to sett the offset, i.e. if the riddle allows requires removal of matches. \n     >-1 means the solutions will include one less match, 1 means one more match, etc. \n     >Only one of the optional parameters can be invoked at once.";

    public static void main(String args[])throws Exception{       
        boolean verbose = false;
        MatchStick solver = new MatchStick();
        
        if(args.length<5){
            System.out.println(info);
            return;
        }
 
        //parse and remove optional cmd args before processing the rest of the arguments
        //check for -m parameter
        //cannot legally be the last parameter
        for(int i = 0; i < args.length;i++){
            if(args[i].equals("-m")){
                solver.numberOfMoves = Integer.parseInt(args[i+1]);
                String[] newArgs = new String[args.length-2];
                for(int j = 0; j < i;j++){
                    newArgs[j] = args[j];
                }
                for(int j = i + 2;j<args.length;j++){
                    newArgs[j-2]  = args[j];
                }
                args = newArgs; 
                //set i to one less so that the argument that took this spot will be parsed
                i--;
            }
            if(args[i].equals("-o")){
                solver.offset = Integer.parseInt(args[i+1]);
                String[] newArgs = new String[args.length-2];
                for(int j = 0; j < i;j++){
                    newArgs[j] = args[j];
                }
                for(int j = i + 2;j<args.length;j++){
                    newArgs[j-2]  = args[j];
                }
                args = newArgs; 
                //set i to one less so that the argument that took this spot will be parsed
                i--;
            }
            if(args[i].equals("-v")){
                verbose = true;
                String[] newArgs = new String[args.length-1];
                for(int j = 0; j < i;j++){
                    newArgs[j] = args[j];

                }
                for(int j = i + 1;j<args.length;j++){
                    newArgs[j-1]  = args[j];
                }
                args = newArgs;
                //set i to one less so that the argument that took this spot will be parsed
                i--;
                continue;
            }
        }
        solver.parseArgs(args);
        
        String fileName = "temp.smv";
        String code = solver.buildExtraDigitModules()+solver.buildCode();
        //find all possible solutions
        while(!solver.solutions.contains("No Solution")){
            solver.stringToFile(code + solver.buildRestictions(),fileName);
            solver.runNuSMV(fileName,verbose);
            //if -v was invoked, end loop after one time
            if(verbose) break;
        }
        
        solver.printSolutions();
    }
    
    //runs nusmv, adding the solution it finds (or if no solution, the String "no solution") to the solution ArrayList
    private void runNuSMV(String fileName,boolean verbose)throws IOException{
        //create arrays to hold our operators and operand;
        String[] solutionNumbers = new String[numArgs.size()];
        String[] solutionOperators = new String[opArgs.size()];
        //fill it with = signs so that when everything else is overwritten, the equal sign will be in its proper place
        Arrays.fill(solutionOperators,"=");
        
        //add multiplicative operator
        for(int i = 0;i< opArgs.size();i++){
            if(opArgs.get(i).equals("X")) solutionOperators[i] = "X";
        }
        
        Runtime rt = Runtime.getRuntime();
        Process pr = rt.exec("nusmv "+fileName);
        
        BufferedReader input = new BufferedReader(new InputStreamReader(pr.getInputStream()));        
        String line=null;
        
        //if an arg is -v for verbose, print out nusmv results instead of parsed results
        if(verbose){
            while((line=input.readLine()) != null) {
                System.out.println(line);
            }
            return;
        }
        
        boolean foundSolution = false;
        //parse to get solution
        while((line=input.readLine()) != null) {
            
            for(int i = 0;i< opArgs.size();i++){                          
                if(line.startsWith("    op"+i+".vertical = ")){
                    solutionOperators[i] = line.substring(19).equals("TRUE") ? "+" : "-";;//assuming i is not double digit;
                }               
            }
            for(int i = 0;i< numArgs.size();i++){                          
                if(line.startsWith("    num"+i+".value =")){
                    solutionNumbers[i] = line.substring(16);//assuming i is not double digit;
                }               
            }
            
            
                     
            if(line.startsWith("    goal = TRUE")){
                foundSolution = true;
                while((line=input.readLine()) != null) {
                    for(int i = 0;i< opArgs.size();i++){                          
                        if(line.startsWith("    op"+i+".vertical =")){
                            solutionOperators[i] = line.substring(19).equals("TRUE") ? "+" : "-";;//assuming i is not double digit;
                        }               
                    }
                    for(int i = 0;i< numArgs.size();i++){                          
                        if(line.startsWith("    num"+i+".value =")){
                            solutionNumbers[i] = line.substring(16);//assuming i is not double digit;
                        }               
                    }
                
                    if(line.startsWith("  ->")){
                        solutions.add(buildSolutionString(solutionOperators,solutionNumbers));
                        restrictSolution(solutionNumbers);
                        return;
                    }
                }
            }

        }  
        if(!foundSolution){
            solutions.add("No Solution");
        }
    }
    
    private void stringToFile(String s,String fileName)throws Exception{
        PrintWriter writer = new PrintWriter(fileName, "UTF-8");
        writer.print(s);
        writer.close();
        
    }
    private String buildCode()throws Exception{ 
        //if numberOfMoves is 0, allow all moves, i.e. don't add the movement constraint to the goal at all
        String moveable = numberOfMoves==-1 ? "" : "& moveable";

        return
        readFile("code.txt")+ "\n" +
        "--if either operand is -1 that means it is not currently a number. We want to ensure all digits are parsable in any solution set.\n"+
        buildParsableConstraint()+        
        "--total offset must equal zero in solution set, unless we actually want an offset.\n"+
        buildBalancedConstraint()+ 
        buildAddedConstraind()+
        buildRemovedConstraind()+
        "--sometimes we wish to restrict the number of matches that are allowed to move\n "+
        "moved := removed >= added ? added : removed;\n"+
        buildMovableConstraint()+
        "--all restrictions on our solution\n"+
        "goal := balanced & parsable & equal "+moveable+";\n"+
        "--the equation must also be true in any solution set\n"+
        buildEqualsAndMainVars();
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
      
    private void printSolutions(){
        //if there is a solution, don't print "no solution" string at the end
        for(String s: solutions){
            if(s.equals("No Solution")&&solutions.size()>1){
                continue;
            }
            System.out.println(s);
        }
        //if solutions were found, print the total number found
        System.out.println("Total Solutions: "+(solutions.size()-1));//-1 to exclude the string "no solution" from counting
    } 

    //we don't care what the operator is, since all digits uniquely identifies a solution, as if the operator where to change, one of the digits would get/lose a match
    private void restrictSolution(String[] solutionNumbers){
        String restriction = "!(TRUE ";
        for(int i = 0; i < solutionNumbers.length;i++){
            restriction += "&(num"+i+".value = "+solutionNumbers[i]+")";
        }
        restriction += ")\n";
        restrictions.add(restriction);    
    }
    
    private void parseArgs(String args[]){

        for(int i =0;i<args.length;i++){
            try{
                Integer.parseInt(args[i]);
                numArgs.add(args[i]);
            }catch (Exception e){
                opArgs.add(args[i]);
            }
        }
    }
    
    private String buildParsableConstraint(){
        // constraint is that all numerical args be parsable
        String parsable = "parsable := TRUE";//we want to add each argument as & (arg(x) != -1), so the left most and sign needs a left operator, i.e TRUE
        for(int i = 0; i < numArgs.size();i++){
            parsable+= " & (num"+i+".value != -1)";
        }
        parsable += ";\n"; 
        return parsable;
    }
    
    private String buildBalancedConstraint(){
        //constraint is that total matches in all operands and operators be balanced
        String balanced = "balanced := "+offset+"=added - removed;";
 
        return balanced;
    }
   
    private String buildMovableConstraint(){
        //constraint is that total moved matches is less than a given number
        String moveable = "moveable := "+numberOfMoves+">=moved;\n";
        return moveable;
    }

    private String buildEqualsAndMainVars(){
        String vars = "VAR\n";
        //create all digits
        for(int i = 0; i < numArgs.size(); i++){
            vars += "num"+i+" :digit"+numArgs.get(i).length()+"(";
            String arg = numArgs.get(i);
            for(int j = 0;j < arg.length();j++){
                int n = Integer.parseInt(String.valueOf(arg.charAt(arg.length()-j-1)));
                vars += n;

                if(j+1!=arg.length()){
                    vars += ",";
                }
            }            
            vars += ");\n";
        }
        
        
        
        
        //create operators. Start with left most one, and it will have left most number as its left operand and second
        //left most number as its right operand. All subsequent operators until we hit = will have previous operator a left operand and next number as right operand
        //however, this inly makes sense if indeed a single side of the equation has more than one number. Otherwise we simply want that number's value
        String leftSide = "";
        String rightSide = "";
        int nextDigit = 0;
        int nextOp = 0;
        if(opArgs.get(0).equals("=")){
            nextDigit = 1;
            nextOp = 1;
            leftSide = "num0.value";
        }else{
            if(opArgs.get(nextOp).equals("X")){
                vars += "op0 : multiplyOperator(num0,num1);\n";    
            }else{
                vars += "op0 : operator("+(opArgs.get(0).equals("+") ? "TRUE" : "FALSE")+",num0,num1);\n";    
            }
            nextDigit = 2;
            nextOp = 1;
         
            while(!opArgs.get(nextOp).equals("=")){
                if(opArgs.get(nextOp).equals("X")){
                    vars += "op"+nextOp+" : multiplyOperator(op"+(nextOp-1)+",num"+nextDigit+");\n";
                }else{
                    String plus = opArgs.get(nextOp).equals("+") ? "TRUE" : "FALSE";
                    vars += "op"+nextOp+" : operator("+plus+",op"+(nextOp-1)+",num"+nextDigit+");\n";
                }
                nextOp++;
                nextDigit++;
            }
            leftSide = "op"+(nextOp-1)+".value";
            nextOp += 1;

        }
        if(nextDigit == numArgs.size()-1){
            rightSide = "num"+nextDigit+".value";
        }else{
            if(opArgs.get(nextOp).equals("X")){
                vars += "op"+nextOp+" : multiplyOperator(num"+nextDigit+",num"+(nextDigit+1)+");\n";                
            }else{
                vars += "op"+nextOp+" : operator("+(opArgs.get(nextOp).equals("+") ? "TRUE" : "FALSE")+",num"+nextDigit+",num"+(nextDigit+1)+");\n";
            }
            nextOp++;
            nextDigit+=2;
            while(nextDigit<numArgs.size()){
                if(opArgs.get(nextOp).equals("X")){
                    vars += "op"+nextOp+" : multiplyOperator(op"+(nextOp-1)+",num"+nextDigit+");\n";
                }else{
                    String plus = opArgs.get(nextOp).equals("+") ? "TRUE" : "FALSE";
                    vars += "op"+nextOp+" : operator("+plus+",op"+(nextOp-1)+",num"+nextDigit+");\n";
                }
                nextOp++;
                nextDigit++;
            }
            rightSide = "op"+(nextOp-1)+".value";
        }
        String equals = "equal :=" + leftSide + "=" + rightSide + ";\n";
        return equals + vars;
    }
    
    private String buildSolutionString(String[] op, String[] num){
        String s = "SOLUTION: " + num[0]+ " ";
        int nextNum = 1;
        int nextOp = 0;
        while(nextNum < num.length){
            s+=op[nextOp];
            s+=num[nextNum] + " ";
            nextNum++;
            nextOp++;
        }
        return s;       
    }

    private String buildRestictions(){
        String restriction = "";
        for(String r:restrictions){
            restriction += "&"+r;
        }
        return "LTLSPEC G !(goal"+restriction+");\n";

        
    }

    private String buildNDigitModule(int numDigits){
        //n0 is right most digit, n1 is to its left, etc.
        String module = "MODULE digit"+numDigits+"(";
        for(int i = 0; i < numDigits;i++){
            if(i!=0){
                module+=",";
            }
            module += "n"+i;
        }
        module += ")\nVAR\n";
        for(int i = 0; i < numDigits;i++){
            module += "d"+i+" : digit1(n"+i+");\n";
        }

        module += "DEFINE\noffset := 0";
        for(int i = 0;i < numDigits;i++){
            module+="+d"+i+".offset";
        }
        module += ";\nmoved := 0";
        
        for(int i = 0;i < numDigits;i++){
            module+="+d"+i+".moved";
        }
        module += ";\n";
        //start building value string
        String parseable = "(FALSE ";
            for(int i = 0;i < numDigits;i++){
                parseable+= "| (d"+i+".value = -1)";
        }
        parseable += ") ? -1 :";
        parseable += " 0";
        for(int i = 0; i <numDigits;i++){
            parseable += "+d"+i+".value*"+(int)Math.pow(10,i);
        }
        
        module += "value :="+parseable +";\n";

        
        
        return module;     
        
    }
    
    private String buildExtraDigitModules(){
        //create a set containing the number of digits in every number we are dealing with
        Set<Integer> digits = new HashSet<>();

        for(String n : numArgs){
            digits.add(n.length());
        }
        String code = "";
        for(int d:digits){
            if(d==1) continue;
            code+= buildNDigitModule(d);
        }
        return code;
    }

    private String buildAddedConstraind(){
        String added = "added := 0";
        for(int i = 0; i < numArgs.size();i++){
            added+= " + num"+i+".added";
        }
        for(int i = 0; i < opArgs.size();i++){
            if(opArgs.get(i).equals("=")) continue;
            added+= " + op"+i+".added";
        }
        added += ";\n"; 
        return added;
    }
    
    private String buildRemovedConstraind(){
        String removed = "removed := 0";
        for(int i = 0; i < numArgs.size();i++){
            removed+= " + num"+i+".removed";
        }
        for(int i = 0; i < opArgs.size();i++){
            if(opArgs.get(i).equals("=")) continue;
            removed+= " + op"+i+".removed";
        }
        removed += ";\n"; 
        return removed;
    }
    
        
    
} 
