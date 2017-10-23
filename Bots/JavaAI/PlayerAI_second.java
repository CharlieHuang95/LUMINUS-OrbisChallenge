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


    int height,width;
    int [][] grid,guarded;

    int guards_needed,n_settlers;
    List<fly> flies;
    List<Point> stationaries;

    int n_nests;
    boolean first_turn;
    List<Point> target_nests;
    List<Point> need_guards;


    public PlayerAI() {
        // Any instantiation code goes here
        n_settlers = 1;
        n_nests = 1;
        first_turn = true;
        target_nests = new ArrayList<Point>();
        flies = new ArrayList<fly>();
        stationaries = new ArrayList<Point>();
        need_guards = new ArrayList<Point>();
        guards_needed=0;
    }

    public class fly {
        int type;
        String ID;
        Point previous, guard_spot;

        public fly(String i, Point p) {
            ID = i;
            previous = p;
        }

    }

    public enum direction{
        UP,DOWN,LEFT,RIGHT;
    }

    /**
     * This method will get called every turn.
     *
     * @param world The latest state of the world.
     * @param friendlyUnits An array containing all remaining firefly units in your team
     * @param enemyUnits An array containing all remaining enemy firefly units
     *
     */

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

    void init_nests(World world){
        Point p = world.getFriendlyNestPositions()[0];
        int x = p.getX(); int y = p.getY();
        if(grid[wAdd(x,1)][hAdd(y,1)]==NEUTRAL)target_nests.add(new Point(wAdd(x,1),hAdd(y,1)));
        if(grid[wAdd(x,-1)][hAdd(y,1)]==NEUTRAL)target_nests.add(new Point(wAdd(x,-1),hAdd(y,1)));
        if(grid[wAdd(x,1)][hAdd(y,-1)]==NEUTRAL)target_nests.add(new Point(wAdd(x,1),hAdd(y,-1)));
        if(grid[wAdd(x,-1)][hAdd(y,-1)]==NEUTRAL)target_nests.add(new Point(wAdd(x,-1),hAdd(y,-1)));
    }
    void update_target_nests(World world){
        Point[] nests = world.getFriendlyNestPositions();
        for (Point p: nests){
            if(target_nests.indexOf(p)!=-1)  target_nests.remove(p);
            int px = p.getX(); int py = p.getY();
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

    boolean on_nest(FriendlyUnit unit, World world){
        Point[] nests = world.getFriendlyNestPositions();
        Point pos = unit.getPosition();
        for(Point n: nests) {
            if(n.equals(pos)) return true;
        }
        return false;
    }

    void kill_adj(World world, FriendlyUnit unit){
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
        if(highest>0) world.move(unit,strongest);
        System.out.println("Killing " + strongest.getY() + " "+ strongest.getY());
    }


    public void doMove(World world, FriendlyUnit[] friendlyUnits, EnemyUnit[] enemyUnits) {
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
                if (num_exits(unit.getPosition()) > 1) {
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
                }
                flies.add(newf);
                index = flies.indexOf(newf);

                fly this_fly = flies.get(index);

                if(this_fly.type==2){
                    if(!unit.getPosition().equals(this_fly.guard_spot)) world.move(unit,this_fly.guard_spot);
                    //System.out.print("____________________O_________________\n"+this_fly.guard_spot.getX()+" "+this_fly.guard_spot.getY());
                }else {
                    //System.out.print("_________________________________________________________________________________\n");
                    List<Point> path = world.getShortestPath(unit.getPosition(),
                            world.getClosestCapturableTileFrom(unit.getPosition(), target_nests).getPosition(), av
                    );
                    if (path != null) world.move(unit, path.get(0));
                }

            }

        }


        for (FriendlyUnit unit: friendlyUnits) {

            if(unit.getLastMoveResult()== MoveResult.NEWLY_SPAWNED) continue;

            int x = unit.getPosition().getX();
            int y = unit.getPosition().getY();
            String id = unit.getUuid();
            int index = find_fly(id);

            System.out.println(id);
            System.out.println(index);



            if(index==-1){
                flies.add(new fly(id,unit.getPosition()));
                index = flies.size()-1;
            }
            fly this_fly = flies.get(index);

            if(this_fly.type==2){
                if(!unit.getPosition().equals(this_fly.guard_spot)) world.move(unit,this_fly.guard_spot);
                else kill_adj(world,unit);

            }else {
                //System.out.print("_________________________________________________________________________________\n");
                List<Point> path = world.getShortestPath(unit.getPosition(),
                        world.getClosestCapturableTileFrom(unit.getPosition(), target_nests).getPosition(), av
                );
                if (path != null) world.move(unit, path.get(0));
            }
        }
    }
}