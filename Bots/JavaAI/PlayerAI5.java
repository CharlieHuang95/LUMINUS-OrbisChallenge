import com.orbischallenge.firefly.client.objects.models.EnemyUnit;
import com.orbischallenge.firefly.client.objects.models.FriendlyUnit;
import com.orbischallenge.firefly.client.objects.models.World;
import com.orbischallenge.firefly.client.objects.models.Tile;

import com.orbischallenge.firefly.objects.enums.MoveResult;
import com.orbischallenge.game.engine.Point;
import com.orbischallenge.firefly.objects.enums.Direction;

import com.orbischallenge.logging.Log;

import java.util.*;

public class PlayerAI {
    int WALL = 1111111, NEUTRAL = 2222222, fNEST=3333333, eNEST=4444444,fTERRITORY=5555555,eTERRITORY=6666666,
            fPERM=7777777,ePERM=8888888;

    int counter,stop_expansion;
    int height,width;
    int [][] grid,guarded;

    int guards_needed,n_settlers;
    List<fly> flies;
    List<Point> stationaries;

    int n_nests;
    boolean first_turn;
    List<Point> target_nests,target_nests2;
    List<Point> need_guards;


    public PlayerAI() {
        // Any instantiation code goes here
        counter = 0;
        stop_expansion=20;
        n_settlers = 1;
        n_nests = 1;
        first_turn = true;
        target_nests = new ArrayList<Point>();
        target_nests2 = new ArrayList<Point>();
        flies = new ArrayList<fly>();
        stationaries = new ArrayList<Point>();
        need_guards = new ArrayList<Point>();
        guards_needed=0;
    }

    public class fly {
        int type,steps,prev_health;
        String ID;
        Point previous, guard_spot,nest_goal;

        public fly(String i, Point p) {
            ID = i;
            previous = p;
            steps=0;
            prev_health=0;
        }

    }

    public void gen_grid(World world, FriendlyUnit[] friendlyUnits, EnemyUnit[] enemyUnits){
        height = world.getHeight();
        width = world.getWidth();
        grid = new int[width][height];
        Point[] fNests = world.getFriendlyNestPositions();
        Point[] eNests = world.getEnemyNestPositions();
        Tile[] tiles= world.getTiles();

        for (int i=0;i<width;i++){
            for(int j=0;j<height;j++) grid[i][j]=WALL;
        }

        for (Tile t: tiles){
            Point pos = t.getPosition();
            if(t.isNeutral()) grid[pos.getX()][pos.getY()] = NEUTRAL;
            else if (t.isFriendly()){
                if(t.isPermanentlyOwned()) grid[pos.getX()][pos.getY()] = fPERM;
                else grid[pos.getX()][pos.getY()] = fTERRITORY;
            }
            else if (t.isEnemy()){
                if(t.isPermanentlyOwned()) grid[pos.getX()][pos.getY()] = ePERM;
                else grid[pos.getX()][pos.getY()] = eTERRITORY;
            }
        }

        for (FriendlyUnit u: friendlyUnits) grid[u.getPosition().getX()][u.getPosition().getY()] = u.getHealth();

        for (EnemyUnit u: enemyUnits) grid[u.getPosition().getX()][u.getPosition().getY()] = u.getHealth();

        for (Point p: fNests) grid[p.getX()][p.getY()] = fNEST;

        for (Point p: eNests) grid[p.getX()][p.getY()] = eNEST;
    }

    public void print_grid(){
        for (int i=0;i<height;i++){
            for(int j=0;j<width;j++) System.out.print(grid[i][j] + " ");
            System.out.print("\n");
        }
    }

    int wAdd(int x, int y){
        if (x+y<0) return x+y+width;
        return (x+y)%width;
    }
    int hAdd(int x,int y){
        if(x+y<0) return x+y+height;
        return (x+y)%height;
    }

    void add_target (int px, int py){
        if(grid[px][py]==NEUTRAL) {
            if(target_nests.indexOf(new Point(wAdd(px,1),py))!=-1) return;
            if(target_nests.indexOf(new Point(wAdd(px,-1),py))!=-1) return;
            if(target_nests.indexOf(new Point(px,hAdd(py,1)))!=-1) return;
            if(target_nests.indexOf(new Point(px,hAdd(py,-1)))!=-1) return;
            target_nests.add(new Point(px,py));
            target_nests2.add(new Point(px,py));
        }
    }

    void init_nests(World world){
        Point p = world.getFriendlyNestPositions()[0];
        int x = p.getX(); int y = p.getY();
        if(grid[wAdd(x,1)][hAdd(y,1)]==NEUTRAL)target_nests.add(new Point(wAdd(x,1),hAdd(y,1)));
        if(grid[wAdd(x,-1)][hAdd(y,1)]==NEUTRAL)target_nests.add(new Point(wAdd(x,-1),hAdd(y,1)));
        if(grid[wAdd(x,1)][hAdd(y,-1)]==NEUTRAL)target_nests.add(new Point(wAdd(x,1),hAdd(y,-1)));
        if(grid[wAdd(x,-1)][hAdd(y,-1)]==NEUTRAL)target_nests.add(new Point(wAdd(x,-1),hAdd(y,-1)));

        target_nests2.addAll(target_nests);
    }
    void update_target_nests(World world){
        Point[] nests = world.getFriendlyNestPositions();
        for (Point p: nests){
            int px = p.getX(); int py = p.getY();
            if(target_nests.indexOf(p)!=-1)  {
                target_nests.remove(p);
                add_target (wAdd(px,2),hAdd(py,1));
                add_target (wAdd(px,2),hAdd(py,-1));
                add_target (wAdd(px,-2),hAdd(py,-1));
                add_target (wAdd(px,-2),hAdd(py,1));
                add_target (wAdd(px,1),hAdd(py,2));
                add_target (wAdd(px,1),hAdd(py,-2));
                add_target (wAdd(px,-1),hAdd(py,-2));
                add_target (wAdd(px,-1),hAdd(py,2));
            }

            if(need_guards.indexOf(new Point(wAdd(px,1),py))==-1&&grid[wAdd(px,1)][py]!=WALL)need_guards.add(new Point(wAdd(px,1),py));
            if(need_guards.indexOf(new Point(wAdd(px,-1),py))==-1&&grid[wAdd(px,-1)][py]!=WALL)need_guards.add(new Point(wAdd(px,-1),py));
            if(need_guards.indexOf(new Point(px,hAdd(py,1)))==-1&&grid[px][hAdd(py,1)]!=WALL)need_guards.add(new Point(px,hAdd(py,1)));
            if(need_guards.indexOf(new Point(px,hAdd(py,-1)))==-1&&grid[px][hAdd(py,-1)]!=WALL)need_guards.add(new Point(px,hAdd(py,-1)));
        }
    }

    int num_exits(Point p){
        int ans = 0;
        int x=p.getX(); int y=p.getY();
        if(guarded[wAdd(x,1)][y]==0&&grid[wAdd(x,1)][y]!=WALL) ans++;
        if(guarded[wAdd(x,-1)][y]==0&&grid[wAdd(x,-1)][y]!=WALL) ans++;
        if(guarded[x][hAdd(y,1)]==0&&grid[x][hAdd(y,1)]!=WALL) ans++;
        if(guarded[x][hAdd(y,-1)]==0&&grid[x][hAdd(y,-1)]!=WALL) ans++;
        return ans++;
    }

    int find_fly(String id){
        for(int i = 0;i<flies.size();i++){
            if(id.equals(flies.get(i).ID)) return i;
        }
        return -1;
    }

    int kill_adj(World world, FriendlyUnit unit){
        Point strongest = new Point(-1,-1);
        int highest = 0;
        int x = unit.getPosition().getX(); int y = unit.getPosition().getY();
        if(grid[wAdd(x,1)][y] > highest && grid[wAdd(x,1)][y]<99999){
            highest = grid[wAdd(x,1)][y];
            strongest = new Point(wAdd(x,1),y);
        }
        if(grid[wAdd(x,-1)][y] > highest && grid[wAdd(x,-1)][y]<99999){
            highest = grid[wAdd(x,-1)][y];
            strongest = new Point(wAdd(x,-1),y);
        }
        if(grid[x][hAdd(y,1)] > highest && grid[x][hAdd(y,1)]<99999){
            highest = grid[x][hAdd(y,1)];
            strongest = new Point(x,hAdd(y,1));
        }
        if(grid[x][hAdd(y,-1)] > highest && grid[x][hAdd(y,-1)]<99999){
            highest = grid[x][hAdd(y,-1)];
            strongest = new Point(x,hAdd(y,-1));
        }
        if(highest>0) {
            world.move(unit,strongest);
            return 1;
        }
        return 0;
    }

    Point closest(Point pos, List<Point> targets,World world){
        int min = 99999999;
        Point best = new Point(-1,-1);

        for(Point point: targets){
            if(world.getTaxiCabDistance(pos,point)<min){
                min = world.getTaxiCabDistance(pos,point);
                best = point;
            }
        }
        return best;
    }

    boolean capturable (int x, int y){
        int v = grid[x][y];
        if (v!=WALL && v!=fTERRITORY && v>99999) return true;
        return false;
    }

    public void doMove(World world, FriendlyUnit[] friendlyUnits, EnemyUnit[] enemyUnits) {
        counter++;
        gen_grid(world,friendlyUnits,enemyUnits);
        print_grid();
        if(first_turn){
            init_nests(world);
            guarded = new int[width][height];
            for(int i=0;i<width;i++){
                for(int j=0; j<height; j++)guarded[i][j]=0;
            }
            first_turn = false;
        }
        update_target_nests(world);


        List<Point> av = new ArrayList<Point>();
        av.addAll(target_nests);
        av.addAll(stationaries);

        for (FriendlyUnit unit: friendlyUnits) {
            int x = unit.getPosition().getX();
            int y = unit.getPosition().getY();
            String id = unit.getUuid();
            int index = find_fly(id);

            if(unit.getLastMoveResult()== MoveResult.NEWLY_SPAWNED) {
                fly newf = new fly(id, unit.getPosition());
                if(!target_nests2.isEmpty() && counter < stop_expansion) {
                    newf.type = 1;
                    newf.nest_goal = closest(unit.getPosition(),target_nests2,world);
                    target_nests2.remove(newf.nest_goal);
                    System.out.println(newf.nest_goal.getX()+" "+newf.nest_goal.getY());
                }
                else if (num_exits(unit.getPosition()) > 1) {
                    newf.type = 2;
                    if (guarded[wAdd(x, 1)][y] == 0 && grid[wAdd(x, 1)][y] != WALL)
                        newf.guard_spot = new Point(wAdd(x, 1), y);
                    else if (guarded[wAdd(x, -1)][y] == 0 && grid[wAdd(x, -1)][y] != WALL)
                        newf.guard_spot = new Point(wAdd(x, -1), y);
                    else if (guarded[x][hAdd(y, 1)] == 0 && grid[x][hAdd(y, 1)] != WALL)
                        newf.guard_spot = new Point(x, hAdd(y, 1));
                    else if (guarded[x][hAdd(y, -1)] == 0 && grid[x][hAdd(y, -1)] != WALL)
                        newf.guard_spot = new Point(x, hAdd(y, -1));
                    guarded[newf.guard_spot.getX()][newf.guard_spot.getY()] = 1;
                    stationaries.add(newf.guard_spot);
                    av.add(newf.guard_spot);
                } else {
                    newf.type = 3;
                }
                flies.add(newf);
                index = flies.indexOf(newf);

                fly this_fly = flies.get(index);

                if(this_fly.type==2){
                    if(!unit.getPosition().equals(this_fly.guard_spot)) world.move(unit,this_fly.guard_spot);
                    //System.out.print("____________________O_________________\n"+this_fly.guard_spot.getX()+" "+this_fly.guard_spot.getY());
                }

            }

        }

        for (FriendlyUnit unit: friendlyUnits) {
            String id = unit.getUuid();
            int index = find_fly(id);

            if (unit.getLastMoveResult() == MoveResult.NEWLY_SPAWNED){
                fly f = flies.get(index);
                if(f.type==2) continue;
            }

            int x = unit.getPosition().getX();
            int y = unit.getPosition().getY();
            Point next_nest;

            if (index == -1) {
                flies.add(new fly(id, unit.getPosition()));
                index = flies.size() - 1;
                if(!target_nests.isEmpty()){
                    next_nest = closest(unit.getPosition(),target_nests,world);
                }
            }

            fly this_fly = flies.get(index);
            if (index == -1) {
                if(!target_nests.isEmpty() && counter < stop_expansion)this_fly.type = 1;
                else this_fly.type = 4;
            }

            if(this_fly.type==1){
                int goalx = this_fly.nest_goal.getX(); int goaly = this_fly.nest_goal.getY();
                if(grid[goalx][goaly]!=NEUTRAL) {
                    this_fly.type = 2;
                    this_fly.guard_spot = unit.getPosition();
                }
                else {
                    List<Point> path;
                    Point goal_tile;
                    goal_tile = new Point(x,y);

                    if(capturable(wAdd(goalx,1),goaly)) goal_tile = new Point(wAdd(goalx,1),goaly);
                    else if(capturable(wAdd(goalx,-1),goaly)) goal_tile = new Point(wAdd(goalx,-1),goaly);
                    else if(capturable(goalx,hAdd(goaly,1))) goal_tile = new Point(goalx,hAdd(goaly,1));
                    else if(capturable(goalx,hAdd(goaly,-1))) goal_tile = new Point(goalx,hAdd(goaly,-1));

                    System.out.println(goal_tile.getX()+" "+goal_tile.getY());

                    path = world.getShortestPath(unit.getPosition(), goal_tile, target_nests);
                    if (path != null) {
                        world.move(unit, path.get(0));
                    }
                }
            }else if(this_fly.type==2) {
                if (!unit.getPosition().equals(this_fly.guard_spot)) world.move(unit, this_fly.guard_spot);
                else if (kill_adj(world, unit) == 0) {
                    if (unit.getHealth() > this_fly.prev_health+3) {
                        this_fly.prev_health = unit.getHealth();
                        this_fly.type = 4;
                    }
                }
            }else if (this_fly.type==4){
                if(this_fly.steps<=3){
                    List<Point> path;
                    path = world.getShortestPath(unit.getPosition(),
                            world.getClosestEnemyNestFrom(unit.getPosition(), target_nests), av
                    );
                    if (unit.getHealth()>12) path = world.getShortestPath(unit.getPosition(),
                            world.getClosestEnemyNestFrom(unit.getPosition(), target_nests), null
                    );
                    if (path != null) {
                        if(guarded[unit.getPosition().getX()][unit.getPosition().getY()]==1) guarded[unit.getPosition().getX()][unit.getPosition().getY()]=0;
                        world.move(unit, path.get(0));
                    }
                    (this_fly.steps)++;
                }
                else {
                    this_fly.type = 2;
                    this_fly.guard_spot = unit.getPosition();
                }

            }else {
                List<Point> path;
                path = world.getShortestPath(unit.getPosition(),
                        world.getClosestEnemyNestFrom(unit.getPosition(), target_nests), av
                );
                if (unit.getHealth()>5) path = world.getShortestPath(unit.getPosition(),
                        world.getClosestEnemyNestFrom(unit.getPosition(), target_nests), null
                );
                if (path != null) world.move(unit, path.get(0));
            }
        }
    }
}