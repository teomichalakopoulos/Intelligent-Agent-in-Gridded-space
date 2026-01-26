package env;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.io.FileWriter;
import java.io.IOException;

import jason.asSyntax.ASSyntax;
import jason.asSyntax.Literal;
import jason.asSyntax.Structure;
import jason.environment.Environment;

public class EnvironmentExt extends Environment {

    // ----------- GRID & STATE -----------
    private int agentX = 1;
    private int agentY = 1;
    private int capacity = 3;
    private int carrying = 0;
    private Random random = new Random();
    private int episode = 0;
    
    // Grid boundaries
    private static final int GRID_SIZE = 6; // 1-5 usable, 0-based array
    
    // Obstacles in the grid
    private boolean[][] obstacle = new boolean[GRID_SIZE][GRID_SIZE];
    
    // Object → (x,y)
    private Map<String, int[]> objects = new HashMap<>();
    
    // Object → whether it is already used/picked up (carried)
    private Map<String, Boolean> used = new HashMap<>();

    // Tracks colored/open state
    private Map<String, Boolean> colored = new HashMap<>();
    private boolean doorOpened = false;
    
    // Reward tracking
    private double episodeReward = 0.0;
    private Map<String, Boolean> awarded = new HashMap<>();
    // episode control
    private int episodesRun = 0;
    private final int episodesToRun = 100;
    private java.util.List<Double> episodeHistory = new java.util.ArrayList<>();
    
    // Auto-exit control: set via system property -Dmas.autoExit=true/false
    // Default is true (for benchmark scripts), set to false to keep window open
    private boolean autoExitOnComplete = Boolean.parseBoolean(System.getProperty("mas.autoExit", "false"));
    
    // For dynamic environment: track if objects can move
    private boolean dynamicObjects = true;
    private int moveCounter = 0;
    // count agent-level actions (used to trigger object moves every 3 actions)
    private int actionCounter = 0;
    // track if this action executed any primitive move (so we don't overwrite step rewards)
    private boolean movedThisAction = false;
    // Track primitive steps per episode for debugging
    private int stepCount = 0;

    // A* PATHFINDING
    class Node implements Comparable<Node> {
        int x, y, g, h;
        Node parent;

        Node(int x, int y, int g, int h, Node parent) {
            this.x = x;
            this.y = y;
            this.g = g;
            this.h = h;
            this.parent = parent;
        }

        int f() { return g + h; }

        @Override
        public int compareTo(Node o) {
            return Integer.compare(this.f(), o.f());
        }
    }

    private java.util.List<String> AStar(int sx, int sy, int gx, int gy) {
        java.util.PriorityQueue<Node> open = new java.util.PriorityQueue<>();
        boolean[][] closed = new boolean[GRID_SIZE][GRID_SIZE];

        open.add(new Node(sx, sy, 0, heuristic(sx, sy, gx, gy), null));

        while (!open.isEmpty()) {
            Node current = open.poll();

            if (current.x == gx && current.y == gy) {
                return reconstructPath(current);
            }

            closed[current.x][current.y] = true;

            int[][] dirs = {{1,0}, {-1,0}, {0,1}, {0,-1}};

            for (int[] d : dirs) {
                int nx = current.x + d[0];
                int ny = current.y + d[1];

                if (nx < 1 || nx >= GRID_SIZE || ny < 1 || ny >= GRID_SIZE)
                    continue;

                if (obstacle[nx][ny])
                    continue;

                if (closed[nx][ny])
                    continue;

                int newG = current.g + 1;
                int newH = heuristic(nx, ny, gx, gy);

                open.add(new Node(nx, ny, newG, newH, current));
            }
        }

        return null; // no path
    }

    private int heuristic(int x, int y, int gx, int gy) {
        return Math.abs(x - gx) + Math.abs(y - gy);
    }

    private java.util.List<String> reconstructPath(Node goal) {
        java.util.LinkedList<String> moves = new java.util.LinkedList<>();

        Node cur = goal;

        while (cur.parent != null) {
            int dx = cur.x - cur.parent.x;
            int dy = cur.y - cur.parent.y;

            if (dx == 1) moves.addFirst("right");
            else if (dx == -1) moves.addFirst("left");
            else if (dy == 1) moves.addFirst("up");
            else if (dy == -1) moves.addFirst("down");

            cur = cur.parent;
        }

        return moves;
    }

    @Override
    public void init(String[] args) {
        // Initialize object positions (some defaults, will randomize episode ones below)
        objects.put("b", new int[]{1,5});
        objects.put("cl", new int[]{5,5});
        objects.put("k", new int[]{1,4});
        objects.put("cd", new int[]{3,5});
        objects.put("t", new int[]{5,1});
        objects.put("ch", new int[]{4,2});
        objects.put("d", new int[]{3,1});

        // Set obstacles
        obstacle[2][1] = true;
        obstacle[2][2] = true;
        obstacle[4][4] = true;
        obstacle[4][5] = true;

        // Initialize usage states
        for(String key : objects.keySet()) {
            used.put(key, false);
            colored.put(key, false);
            awarded.put(key, false);
        }
        doorOpened = false;

        // Randomize positions for the first episode for t,ch,d 
        randomizeEpisodePositions();

        publishPercepts(false);
    }

    // ----------- DYNAMIC ENVIRONMENT METHODS -----------
    private void moveObjectRandomly(String obj) {
        if (!objects.containsKey(obj) || used.getOrDefault(obj, false)) {
            return;
        }

        int attempts = 0;
        int[] pos = objects.get(obj);
        int newX = pos[0], newY = pos[1];
        
        // Try to find a valid new position
        while (attempts < 10) {
            int[][] directions = {{1,0}, {-1,0}, {0,1}, {0,-1}};
            int[] dir = directions[random.nextInt(4)];
            
            newX = pos[0] + dir[0];
            newY = pos[1] + dir[1];
            
            // Check bounds, obstacles, and avoid other objects
            if (newX >= 1 && newX < GRID_SIZE && newY >= 1 && newY < GRID_SIZE &&
                !obstacle[newX][newY] && !isObjectAt(newX, newY)) {
                break;
            }
            attempts++;
        }
        
        if (attempts < 10) {
            objects.put(obj, new int[]{newX, newY});
            System.out.println("[ENV] Object " + obj + " moved to (" + newX + "," + newY + ")");
        }
    }

    private boolean isObjectAt(int x, int y) {
        for (int[] pos : objects.values()) {
            if (pos[0] == x && pos[1] == y) {
                return true;
            }
        }
        return false;
    }

    // Update environment state (called when actionCounter % 3 == 0)
    public void updateEnvironment() {
        if (!dynamicObjects) return;

        // Always move t, ch, d if not carried
        String[] critical = {"t", "ch", "d"};
        for (String obj : critical) {
            if (objects.containsKey(obj) && !used.getOrDefault(obj, false)) {
                moveObjectRandomly(obj);
            }
        }
    }

    // ----------- UPDATE PERCEPTS -----------
    private void publishPercepts(boolean applyStepCost) {
        clearPercepts();

        // Agent's own state
        addPercept(Literal.parseLiteral("position(" + agentX + "," + agentY + ")"));
        // expose carry_count (agent expects carry_count(N))
        addPercept(Literal.parseLiteral("carry_count(" + carrying + ")"));
        addPercept(Literal.parseLiteral("capacity(" + capacity + ")"));

        // Add percepts for carried objects
        for (Map.Entry<String, Boolean> entry : used.entrySet()) {
            if (entry.getValue()) {
                addPercept(Literal.parseLiteral("carrying(" + entry.getKey() + ")"));
            }
        }

        // Add percepts for object locations (if not carried)
        for (Map.Entry<String, int[]> entry : objects.entrySet()) {
            String obj = entry.getKey();
            if (!used.getOrDefault(obj, false)) {
                int[] pos = entry.getValue();
                addPercept(Literal.parseLiteral("location(" + obj + "," + pos[0] + "," + pos[1] + ")"));
            }
        }

        // Add colored/open percepts
        for (Map.Entry<String, Boolean> e : colored.entrySet()) {
            if (e.getValue()) addPercept(Literal.parseLiteral("colored(" + e.getKey() + ")"));
        }
        if (doorOpened) addPercept(Literal.parseLiteral("open(d)"));

        // Compute rewards for this step (detailed breakdown for debugging)
        double stepReward = 0.0;
        double baseCost = 0.0;
        double carryCost = 0.0;
        double incompatibleCost = 0.0;
        double goalsAwardedThisStep = 0.0;
        int incompatible = 0;

        if (applyStepCost) {
            // increment step counter (each time we apply step cost it's a primitive move)
            stepCount++;
            if (carrying == 0) {
                baseCost = -0.01;
            } else {
                carryCost = -0.02 * carrying;
                // count incompatible items (not among tools b,cl,k,cd)
                for (String obj : used.keySet()) {
                    if (used.getOrDefault(obj, false)) {
                        if (!(obj.equals("b") || obj.equals("cl") || obj.equals("k") || obj.equals("cd"))) {
                            incompatible++;
                        }
                    }
                }
                incompatibleCost = -0.03 * incompatible;
            }
            stepReward += baseCost + carryCost + incompatibleCost;
        }

        // Add goal rewards once when achieved (applies even if no step cost)
        if (colored.getOrDefault("t", false) && !awarded.getOrDefault("t", false)) {
            stepReward += 1.0; awarded.put("t", true); goalsAwardedThisStep += 1.0;
        }
        if (colored.getOrDefault("ch", false) && !awarded.getOrDefault("ch", false)) {
            stepReward += 1.0; awarded.put("ch", true); goalsAwardedThisStep += 1.0;
        }
        if (doorOpened && !awarded.getOrDefault("d", false)) {
            stepReward += 0.8; awarded.put("d", true); goalsAwardedThisStep += 0.8;
        }

        episodeReward += stepReward;

        // debug print of reward breakdown
        System.out.println(String.format("[ENV] step reward breakdown: applyStepCost=%b, base=%.3f, carry=%.3f (#carry=%d), incompatible=%.3f (#incompat=%d), goals_awarded=%.3f => step=%.3f, episode=%.3f",
            applyStepCost, baseCost, carryCost, carrying, incompatibleCost, incompatible, goalsAwardedThisStep, stepReward, episodeReward));

        // expose reward percepts
        addPercept(Literal.parseLiteral("step_reward(" + stepReward + ")"));
        addPercept(Literal.parseLiteral("episode_reward(" + episodeReward + ")"));

        // Add obstacle percepts in vicinity
        for (int x = Math.max(1, agentX-2); x <= Math.min(GRID_SIZE-1, agentX+2); x++) {
            for (int y = Math.max(1, agentY-2); y <= Math.min(GRID_SIZE-1, agentY+2); y++) {
                if (obstacle[x][y]) {
                    addPercept(Literal.parseLiteral("obstacle(" + x + "," + y + ")"));
                }
            }
        }

        // Check if episode goals achieved
        if (colored.getOrDefault("t", false) && colored.getOrDefault("ch", false) && doorOpened) {
            // record episode reward
            System.out.println("[ENV] Episode completed with reward: " + episodeReward + ", steps=" + stepCount + ", positions: t@(" + objects.get("t")[0] + "," + objects.get("t")[1] + "), ch@(" + objects.get("ch")[0] + "," + objects.get("ch")[1] + "), d@(" + objects.get("d")[0] + "," + objects.get("d")[1] + ")");
            
            // Write reward to file for external script to read (append mode)
            try (FileWriter fw = new FileWriter("episode_rewards.txt", true)) {
                fw.write(episodeReward + "\n");
            } catch (IOException e) {
                System.err.println("[ENV] Failed to write reward file: " + e.getMessage());
            }
            
            // Exit after episode completes if autoExit is enabled
            if (autoExitOnComplete) {
                System.out.println("[ENV] Episode done, exiting (autoExit=true)...");
                System.exit(0);
            } else {
                System.out.println("[ENV] Episode done. Window stays open (autoExit=false).");
            }
            
            episodeHistory.add(episodeReward);
            episodesRun++;

            if (episodesRun >= episodesToRun) {
                // print summary
                double sum = 0.0;
                for (double r : episodeHistory) sum += r;
                double avg = sum / episodeHistory.size();
                System.out.println("[ENV] Completed " + episodesRun + " episodes. Average episode reward: " + avg);
                // stop dynamic changes
                dynamicObjects = false;
            } else {
                // reset for next episode
                resetEpisode();
            }
        }
    }

    // ----------- MAIN ACTION HANDLER -----------
    @Override
    public boolean executeAction(String agName, Structure action) {
        String act = action.getFunctor();
        System.out.println("[ENV] Received action: " + action);

        // reset per-action moved flag
        movedThisAction = false;

        boolean result = false;
        switch (act) {
            case "move":
                result = doMove(action);
                break;
            case "pickup":
                result = doPickup(action);
                break;
            case "drop":
                result = doDrop(action);
                break;
            case "paint":
                result = doPaint(action);
                break;
            case "open":
                result = doOpen(action);
                break;
            case "path_to":
                result = doPathTo(action);
                break;
            case "goto_coord":
                result = doGotoCoord(action);
                break;
            case "explore":
                result = doExplore(action);  // Will never happen because objects are always known
                break;
            case "reset_episode":
                resetEpisode();
                result = true;
                break;
            default:
                System.out.println("[ENV] Unknown action: " + action);
                return false;
        }

        // increment action counter and trigger dynamic moves every 3 actions
        actionCounter++;
        if (dynamicObjects && actionCounter % 3 == 0) {
            updateEnvironment();
        }

        // After the action, publish percepts.
        // If any primitive move occurred during the action, doMove already published step-cost percepts,
        // so avoid calling publishPercepts(false) which would overwrite them.
        if (!act.equals("move") && !movedThisAction) {
            publishPercepts(false);
        }

        return result;
    }

    // ----------- ACTION IMPLEMENTATIONS -----------
    private boolean doMove(Structure action) {
        String dir = action.getTerm(0).toString();
        System.out.println("[ENV] Moving: " + dir);
        
        int newX = agentX;
        int newY = agentY;

        switch (dir) {
            case "up":
                if (agentY < GRID_SIZE-1) newY++;
                break;
            case "down":
                if (agentY > 1) newY--;
                break;
            case "right":
                if (agentX < GRID_SIZE-1) newX++;
                break;
            case "left":
                if (agentX > 1) newX--;
                break;
            default:
                System.out.println("[ENV] Unknown direction: " + dir);
                return false;
        }

        // Check obstacles
        if (obstacle[newX][newY]) {
            System.out.println("[ENV] Obstacle at (" + newX + "," + newY + ")");
            return false;
        }

        agentX = newX;
        agentY = newY;
        System.out.println("[ENV] New position: (" + agentX + "," + agentY + ")");

        // mark that a primitive move occurred in this action
        movedThisAction = true;

        // moving is primitive: publish percepts and apply step cost for this primitive move
        publishPercepts(true);

        // moving is primitive: updateEnvironment is triggered per-action centrally in executeAction

        return true;
    }

    private boolean doPickup(Structure action) {
        String obj = action.getTerm(0).toString();
        System.out.println("[ENV] Pickup attempt: " + obj);

        if (!objects.containsKey(obj)) {
            System.out.println("[ENV] Unknown object: " + obj);
            return false;
        }

        if (carrying >= capacity) {
            System.out.println("[ENV] Capacity full: " + carrying + "/" + capacity);
            return false;
        }

        int[] pos = objects.get(obj);

        if (pos[0] == agentX && pos[1] == agentY) {
            carrying++;
            used.put(obj, true);
            System.out.println("[ENV] Picked up " + obj + ". Now carrying: " + carrying);
            return true;
        } else {
            System.out.println("[ENV] Object not at agent position. Object at (" + 
                             pos[0] + "," + pos[1] + "), Agent at (" + agentX + "," + agentY + ")");
            return false;
        }
    }

    private boolean doDrop(Structure action) {
        String obj = action.getTerm(0).toString();
        System.out.println("[ENV] Drop attempt: " + obj);

        if (!used.getOrDefault(obj, false)) {
            System.out.println("[ENV] Not carrying: " + obj);
            return false;
        }

        // Drop object at current position
        carrying--;
        used.put(obj, false);
        objects.put(obj, new int[]{agentX, agentY});
        
        System.out.println("[ENV] Dropped " + obj + " at (" + agentX + "," + agentY + "). Now carrying: " + carrying);
        return true;
    }

    private boolean doPaint(Structure action) {
        String obj = action.getTerm(0).toString();

        if (!obj.equals("t") && !obj.equals("ch")) {
            System.out.println("[ENV] Can only paint t or ch");
            return false;
        }

        if (!used.getOrDefault("b", false) || !used.getOrDefault("cl", false)) {
            System.out.println("[ENV] Need brush (b) and color (cl) to paint");
            return false;
        }

        int[] pos = objects.get(obj);
        
        // Υπολογισμός απόστασης (Manhattan): |x1-x2| + |y1-y2|
        int dist = Math.abs(pos[0] - agentX) + Math.abs(pos[1] - agentY);

        // Αν είμαστε πάνω στο αντικείμενο (0) ή δίπλα του (1), το βάφουμε
        if (dist <= 1) {
            // mark colored without affecting carrying state
            colored.put(obj, true);
            System.out.println("[ENV] Painted: " + obj);
            return true;
        } else {
            System.out.println("[ENV] Paint failed. Agent at (" + agentX + "," + agentY + ") but Object at (" + pos[0] + "," + pos[1] + ")");
            return false;
        }
    }

    private boolean doOpen(Structure action) {
        String obj = action.getTerm(0).toString();

        if (!obj.equals("d")) {
            System.out.println("[ENV] Can only open door (d)");
            return false;
        }

        if (!used.getOrDefault("k", false) || !used.getOrDefault("cd", false)) {
            System.out.println("[ENV] Need key (k) and card (cd) to open door");
            return false;
        }

        int[] pos = objects.get(obj);
        
        // Ίδια λογική απόστασης και για την πόρτα
        int dist = Math.abs(pos[0] - agentX) + Math.abs(pos[1] - agentY);

        if (dist <= 1) {
            doorOpened = true;
            System.out.println("[ENV] Door opened!");
            return true;
        } else {
            System.out.println("[ENV] Open failed. Agent at (" + agentX + "," + agentY + ") but Door at (" + pos[0] + "," + pos[1] + ")");
            return false;
        }
    }

    private boolean doPathTo(Structure action) {
        String obj = action.getTerm(0).toString();
        System.out.println("[ENV] Path finding to: " + obj);

        if (!objects.containsKey(obj)) {
            System.out.println("[ENV] Object not found: " + obj);
            return false;
        }

        int[] goal = objects.get(obj);
        int gx = goal[0];
        int gy = goal[1];

        java.util.List<String> path = AStar(agentX, agentY, gx, gy);

        if (path == null) {
            System.out.println("[ENV] No path found to " + obj);
            return false;
        }

        System.out.println("[ENV] Found path with " + path.size() + " steps: " + path);

        // Execute moves
        for (String dir : path) {
            Structure moveAction = ASSyntax.createStructure("move", ASSyntax.createAtom(dir));
            if (!doMove(moveAction)) {
                System.out.println("[ENV] Move failed: " + dir);
                return false;
            }
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {}
        }

        return true;
    }

    private boolean doGotoCoord(Structure action) {
        try {
            int targetX = Integer.parseInt(action.getTerm(0).toString());
            int targetY = Integer.parseInt(action.getTerm(1).toString());
            
            System.out.println("[ENV] Going to coordinates: (" + targetX + "," + targetY + ")");
            
            if (targetX < 1 || targetX >= GRID_SIZE || targetY < 1 || targetY >= GRID_SIZE) {
                System.out.println("[ENV] Coordinates out of bounds");
                return false;
            }
            
            java.util.List<String> path = AStar(agentX, agentY, targetX, targetY);
            
            if (path == null) {
                System.out.println("[ENV] No path to coordinates");
                return false;
            }
            
            System.out.println("[ENV] Found path with " + path.size() + " steps");
            
            for (String dir : path) {
                Structure moveAction = ASSyntax.createStructure("move", ASSyntax.createAtom(dir));
                if (!doMove(moveAction)) {
                    return false;
                }
                try {
                    Thread.sleep(300);
                } catch (InterruptedException e) {}
            }
            
            return true;
        } catch (Exception e) {
            System.out.println("[ENV] Error in goto_coord: " + e.getMessage());
            return false;
        }
    }

    private boolean doExplore(Structure action) {   // Will never happen because objects are always known
        System.out.println("[ENV] Exploration mode");
        
        // Simple exploration: move in a pattern to discover objects
        String[] explorationPattern = {"right", "right", "down", "down", "left", "left", "up", "up"};
        
        for (String dir : explorationPattern) {
            Structure moveAction = ASSyntax.createStructure("move", ASSyntax.createAtom(dir));
            if (!doMove(moveAction)) {
                // Try alternative direction if blocked
                String altDir = getAlternativeDirection(dir);
                moveAction = ASSyntax.createStructure("move", ASSyntax.createAtom(altDir));
                if (!doMove(moveAction)) {
                    System.out.println("[ENV] Exploration blocked");
                    break;
                }
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {}
        }
        
        return true;
    }

    private String getAlternativeDirection(String dir) {
        switch (dir) {
            case "up": return "right";
            case "down": return "left";
            case "right": return "down";
            case "left": return "up";
            default: return "right";
        }
    }

    // New helper to randomize positions for t,ch,d each episode
    private void randomizeEpisodePositions() {
        // place t, ch, d randomly on free cells (not obstacles, not overlapping, not at agent start)
        String[] epis = {"t","ch","d"};
        for (String obj : epis) {
            int attempts = 0;
            while (attempts < 50) {
                int x = 1 + random.nextInt(GRID_SIZE-1);
                int y = 1 + random.nextInt(GRID_SIZE-1);
                if (!obstacle[x][y] && !isObjectAt(x,y) && !(x==1 && y==1)) {
                    objects.put(obj, new int[]{x,y});
                    break;
                }
                attempts++;
            }
        }
        System.out.println("[ENV] Episode positions randomized: t@(" + objects.get("t")[0] + "," + objects.get("t")[1] + "), ch@(" + objects.get("ch")[0] + "," + objects.get("ch")[1] + "), d@(" + objects.get("d")[0] + "," + objects.get("d")[1] + ")");
    }

    // Reset environment to start a new episode
    private void resetEpisode() {
        episode++;
        System.out.println("[ENV] Resetting episode " + episode);
        // reset agent
        agentX = 1; agentY = 1; carrying = 0; moveCounter = 0; doorOpened = false;
        // reset step counter
        stepCount = 0;
         // reset carried/used flags
         for (String k : used.keySet()) used.put(k, false);
         // reset colored flags
         for (String k : colored.keySet()) colored.put(k, false);
         // reset awarded flags and episode reward
         for (String k : awarded.keySet()) awarded.put(k, false);
         episodeReward = 0.0;
         // randomize episode objects t,ch,d
         randomizeEpisodePositions();
         publishPercepts(false);
    }

 }