// DYNAMIC GOAL-SELECTION AGENT - REWARD-BASED DECISION MAKING

!start.

position(1,1).
capacity(3).
+carry_count(0).

// Goal rewards (matching environment rewards)
reward(paint_table, 1.0).
reward(paint_chair, 1.0).
reward(open_door, 0.8).

// Track completed goals
completed(none).

// Basic action plans
+!pickup(Obj)
   : location(Obj, X, Y)
   <- .print("Going to pickup ", Obj, " at (", X, ",", Y, ")");
      goto_coord(X, Y);
      pickup(Obj).

+!drop(Obj)
   : carrying(Obj) & position(X,Y)
   <- .print("Dropping ", Obj);
      drop(Obj).

+!drop(Obj)
   : not carrying(Obj)
   <- .print("Not carrying ", Obj, ", nothing to drop").

+!paint(Obj)
   : location(Obj, X, Y) & carrying(b) & carrying(cl)
   <- .print("Going to paint ", Obj, " at (", X, ",", Y, ")");
      goto_coord(X, Y);
      paint(Obj);
      +colored(Obj).

+!open(Obj)
   : location(Obj, X, Y) & carrying(k) & carrying(cd)
   <- .print("Going to open ", Obj, " at (", X, ",", Y, ")");
      goto_coord(X, Y);
      open(Obj);
      +open(Obj).

// ============================================
// MAIN MISSION - DYNAMIC GOAL SELECTION
// ============================================
+!start
   <- .print("!!! DYNAMIC REWARD-BASED MISSION START !!!");
      !execute_all_goals;
      .print("!!! MISSION COMPLETE !!!").

// Execute all goals by repeatedly choosing the best one
+!execute_all_goals
   : completed(paint_table) & completed(paint_chair) & completed(open_door)
   <- .print("All goals completed!").

+!execute_all_goals
   <- .print("Evaluating remaining goals...");
      !choose_best_goal(Goal);
      .print("Selected goal: ", Goal);
      !execute_goal(Goal);
      !execute_all_goals.

// ============================================
// GOAL SELECTION BASED ON EXPECTED REWARD
// ============================================

// Calculate expected utility for each goal
// Utility = Reward - EstimatedCost
// EstimatedCost considers: distance to target + distance to tools if not carrying

+!choose_best_goal(Goal)
   <- !calculate_utilities(Utilities);
      !select_max_utility(Utilities, Goal);
      .print("Goal utilities: ", Utilities, " -> Best: ", Goal).

// Calculate utility for all available goals
+!calculate_utilities(Utilities)
   <- !get_goal_utility(paint_table, U1);
      !get_goal_utility(paint_chair, U2);
      !get_goal_utility(open_door, U3);
      Utilities = [[paint_table, U1], [paint_chair, U2], [open_door, U3]].

// Get utility for paint_table
+!get_goal_utility(paint_table, Utility)
   : completed(paint_table)
   <- Utility = -9999.  // Already done, lowest priority

+!get_goal_utility(paint_table, Utility)
   : not completed(paint_table) & reward(paint_table, R) & 
     location(t, TX, TY) & position(AX, AY)
   <- !estimate_cost(paint_table, TX, TY, Cost);
      Utility = R - Cost;
      .print("paint_table utility: reward=", R, " cost=", Cost, " utility=", Utility).

+!get_goal_utility(paint_table, Utility)
   : not completed(paint_table) & reward(paint_table, R)
   <- // Don't know where table is, assume medium cost
      Utility = R - 0.5;
      .print("paint_table utility (unknown location): ", Utility).

// Get utility for paint_chair
+!get_goal_utility(paint_chair, Utility)
   : completed(paint_chair)
   <- Utility = -9999.

+!get_goal_utility(paint_chair, Utility)
   : not completed(paint_chair) & reward(paint_chair, R) & 
     location(ch, CX, CY) & position(AX, AY)
   <- !estimate_cost(paint_chair, CX, CY, Cost);
      Utility = R - Cost;
      .print("paint_chair utility: reward=", R, " cost=", Cost, " utility=", Utility).

+!get_goal_utility(paint_chair, Utility)
   : not completed(paint_chair) & reward(paint_chair, R)
   <- Utility = R - 0.5;
      .print("paint_chair utility (unknown location): ", Utility).

// Get utility for open_door
+!get_goal_utility(open_door, Utility)
   : completed(open_door)
   <- Utility = -9999.

+!get_goal_utility(open_door, Utility)
   : not completed(open_door) & reward(open_door, R) & 
     location(d, DX, DY) & position(AX, AY)
   <- !estimate_cost(open_door, DX, DY, Cost);
      Utility = R - Cost;
      .print("open_door utility: reward=", R, " cost=", Cost, " utility=", Utility).

+!get_goal_utility(open_door, Utility)
   : not completed(open_door) & reward(open_door, R)
   <- Utility = R - 0.5;
      .print("open_door utility (unknown location): ", Utility).

// Estimate cost based on distance and tool requirements
// Cost = distance_to_target * step_cost + tool_acquisition_cost

+!estimate_cost(paint_table, TX, TY, Cost)
   : position(AX, AY) & carrying(b) & carrying(cl)
   <- // Already have tools, just distance cost
      Dist = math.abs(TX - AX) + math.abs(TY - AY);
      Cost = Dist * 0.02.  // ~0.02 per step with tools

+!estimate_cost(paint_table, TX, TY, Cost)
   : position(AX, AY)
   <- // Need to get tools first, add extra cost
      Dist = math.abs(TX - AX) + math.abs(TY - AY);
      Cost = Dist * 0.02 + 0.2.  // Extra cost for tool acquisition

+!estimate_cost(paint_chair, CX, CY, Cost)
   : position(AX, AY) & carrying(b) & carrying(cl)
   <- Dist = math.abs(CX - AX) + math.abs(CY - AY);
      Cost = Dist * 0.02.

+!estimate_cost(paint_chair, CX, CY, Cost)
   : position(AX, AY)
   <- Dist = math.abs(CX - AX) + math.abs(CY - AY);
      Cost = Dist * 0.02 + 0.2.

+!estimate_cost(open_door, DX, DY, Cost)
   : position(AX, AY) & carrying(k) & carrying(cd)
   <- Dist = math.abs(DX - AX) + math.abs(DY - AY);
      Cost = Dist * 0.02.

+!estimate_cost(open_door, DX, DY, Cost)
   : position(AX, AY)
   <- Dist = math.abs(DX - AX) + math.abs(DY - AY);
      Cost = Dist * 0.02 + 0.2.

// Select the goal with maximum utility
+!select_max_utility([[G1, U1]], G1).

+!select_max_utility([[G1, U1], [G2, U2] | Rest], Best)
   : U1 >= U2
   <- !select_max_utility([[G1, U1] | Rest], Best).

+!select_max_utility([[G1, U1], [G2, U2] | Rest], Best)
   : U1 < U2
   <- !select_max_utility([[G2, U2] | Rest], Best).

// ============================================
// GOAL EXECUTION
// ============================================

+!execute_goal(paint_table)
   <- .print("Executing goal: paint_table");
      !ensure_paint_tools;
      !paint_table;
      +completed(paint_table);
      !drop_paint_tools_if_done.

+!execute_goal(paint_chair)
   <- .print("Executing goal: paint_chair");
      !ensure_paint_tools;
      !paint_chair;
      +completed(paint_chair);
      !drop_paint_tools_if_done.

+!execute_goal(open_door)
   <- .print("Executing goal: open_door");
      !ensure_door_tools;
      !open_door;
      +completed(open_door);
      !drop_door_tools.

// Ensure we have the right tools
+!ensure_paint_tools
   : carrying(b) & carrying(cl)
   <- .print("Already have painting tools").

+!ensure_paint_tools
   : not carrying(b) | not carrying(cl)
   <- .print("Getting painting tools");
      !drop_incompatible_tools(paint);
      !acquire(b);
      !acquire(cl).

+!ensure_door_tools
   : carrying(k) & carrying(cd)
   <- .print("Already have door tools").

+!ensure_door_tools
   : not carrying(k) | not carrying(cd)
   <- .print("Getting door tools");
      !drop_incompatible_tools(door);
      !acquire(k);
      !acquire(cd).

// Drop tools that are incompatible with current goal
+!drop_incompatible_tools(paint)
   : carrying(k)
   <- !drop(k); !drop_incompatible_tools(paint).

+!drop_incompatible_tools(paint)
   : carrying(cd)
   <- !drop(cd); !drop_incompatible_tools(paint).

+!drop_incompatible_tools(paint).

+!drop_incompatible_tools(door)
   : carrying(b)
   <- !drop(b); !drop_incompatible_tools(door).

+!drop_incompatible_tools(door)
   : carrying(cl)
   <- !drop(cl); !drop_incompatible_tools(door).

+!drop_incompatible_tools(door).

// Drop paint tools only if both paint goals are done
+!drop_paint_tools_if_done
   : completed(paint_table) & completed(paint_chair)
   <- .print("Both painting goals done, dropping paint tools");
      !drop(b);
      !drop(cl).

+!drop_paint_tools_if_done
   <- .print("More painting to do, keeping tools").

+!drop_door_tools
   <- .print("Door goal done, dropping door tools");
      !drop(k);
      !drop(cd).

// ============================================
// TOOL ACQUISITION
// ============================================

+!acquire(Tool)
   : carrying(Tool)
   <- .print("Already carrying ", Tool).

+!acquire(Tool)
   : location(Tool, X, Y) & carry_count(C) & capacity(Cap) & C < Cap
   <- .print("Acquiring ", Tool, " - capacity available");
      goto_coord(X, Y);
      !pickup(Tool).

+!acquire(Tool)
   : location(Tool, X, Y) & carry_count(C) & capacity(Cap) & C >= Cap
   <- .print("Acquiring ", Tool, " - capacity full, dropping something");
      !make_room;
      goto_coord(X, Y);
      !pickup(Tool).

+!acquire(Tool)
   : not location(Tool, _, _)
   <- .print("Don't know where ", Tool, ". Exploring...");
      explore;
      .wait(200);
      !acquire(Tool).

// Make room by dropping an item
+!make_room
   : carrying(b) & not (completed(paint_table) | completed(paint_chair))
   <- .print("Can't drop paint tool, need it"); !make_room_other.

+!make_room
   : carrying(b)
   <- .print("Dropping brush"); !drop(b).

+!make_room
   : carrying(cl)
   <- .print("Dropping color"); !drop(cl).

+!make_room
   : carrying(k)
   <- .print("Dropping key"); !drop(k).

+!make_room
   : carrying(cd)
   <- .print("Dropping crowbar"); !drop(cd).

+!make_room
   : carrying(Item)
   <- .print("Dropping ", Item); !drop(Item).

+!make_room_other
   : carrying(k)
   <- !drop(k).

+!make_room_other
   : carrying(cd)
   <- !drop(cd).

// ============================================
// TASK EXECUTION PLANS
// ============================================

// Paint table
+!paint_table
   : location(t, X, Y) & carrying(b) & carrying(cl)
   <- goto_coord(X, Y);
      paint(t).

+!paint_table
   : not location(t, _, _) & carrying(b) & carrying(cl)
   <- .print("Searching for table...");
      explore;
      .wait(300);
      !paint_table.

+!paint_table
   : not (carrying(b) & carrying(cl))
   <- .print("Need painting tools first");
      !ensure_paint_tools;
      !paint_table.

// Paint chair
+!paint_chair
   : location(ch, X, Y) & carrying(b) & carrying(cl)
   <- goto_coord(X, Y);
      paint(ch).

+!paint_chair
   : not location(ch, _, _) & carrying(b) & carrying(cl)
   <- .print("Searching for chair...");
      explore;
      .wait(300);
      !paint_chair.

+!paint_chair
   : not (carrying(b) & carrying(cl))
   <- .print("Need painting tools first");
      !ensure_paint_tools;
      !paint_chair.

// Open door
+!open_door
   : location(d, X, Y) & carrying(k) & carrying(cd)
   <- goto_coord(X, Y);
      open(d).

+!open_door
   : not location(d, _, _) & carrying(k) & carrying(cd)
   <- .print("Searching for door...");
      explore;
      .wait(300);
      !open_door.

+!open_door
   : not (carrying(k) & carrying(cd))
   <- .print("Need door tools first");
      !ensure_door_tools;
      !open_door.

// ============================================
// ERROR HANDLING
// ============================================

+pickup(Obj)[error(action_failed)]
   : carry_count(C) & capacity(Cap) & C >= Cap
   <- .print("Pickup failed - capacity full");
      !make_room;
      !acquire(Obj).

+pickup(Obj)[error(action_failed)]
   : location(Obj, X, Y) & not (position(X, Y))
   <- .print("Pickup failed - not at object location");
      goto_coord(X, Y);
      pickup(Obj).

+paint(Obj)[error(action_failed)]
   : not (carrying(b) & carrying(cl))
   <- .print("Paint failed - missing tools");
      !ensure_paint_tools;
      !paint(Obj).

+open(Obj)[error(action_failed)]
   : not (carrying(k) & carrying(cd))
   <- .print("Open failed - missing tools");
      !ensure_door_tools;
      !open(Obj).
