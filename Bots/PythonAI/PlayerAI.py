from PythonClientAPI.Game import PointUtils
from PythonClientAPI.Game.Entities import FriendlyUnit, EnemyUnit, Tile
from PythonClientAPI.Game.Enums import Direction, MoveType, MoveResult
from PythonClientAPI.Game.World import World
import queue as Q
import random

def print_array(array):
    for row in array:
        print(row)

""" ------------------------------------------CREATE HELPER ARRAYS ------------------------------"""

class HelperArray:
    def __init__(self, t, world):
        self.world = world
        if t == 'adjacent_walls':
            self.help = self.create_adjacent_walls(world)
            
    def create_adjacent_walls(self, world):
        # Figure out where the walls are and optimal nest locations
        helper = [[0] * self.world.get_width() for x in range(self.world.get_height())]
        for y in range(world.get_height()):
            for x in range(world.get_width()):
                if world.is_wall((x, y)):
                    top = self.get_wrap_around_coordinate((x, y-1))
                    helper[top[1]][top[0]] += 1
                    bot = self.get_wrap_around_coordinate((x, y+1))
                    helper[bot[1]][bot[0]] += 1
                    left = self.get_wrap_around_coordinate((x-1, y))
                    helper[left[1]][left[0]] += 1
                    right = self.get_wrap_around_coordinate((x+1, y))
                    helper[right[1]][right[0]] += 1
        for y in range(world.get_height()):
            for x in range(world.get_width()):
                if self.world.is_wall((x, y)):
                    helper[x][y] = 0
        #print_array(helper)
        return helper

    def get_wrap_around_coordinate(self, coor):
        return (coor[0] % self.world.get_width(), coor[1] % self.world.get_height())

class State:
    def __init__(self):
        self.state = "EXPAND"
        self.aggression = 1
        self.defense = 1
    def get_state(self):
        return self.state
    def get_aggression(self):
        return self.aggression
    def get_defense(self):
        return self.defense

# UnitMachine each controls one unit.
# Roles include:
# 1. Scavenger - Finds new nests
# 2. Destroyer - Finds enemy nests to destroy
# 3. Protector - Stays near its assigned nest and detects enemy presence

class UnitMachine:
    def __init__(self, unit, nests_list, world, defenders, state):
        self.unit = unit
        self.target = None
        self.role = self.get_role(unit.position, world, defenders, state)
        self.nests_list = nests_list

    def get_role(self, pos, world, defenders, state):
        chosen = random.randint(1, 10)
        if chosen <= state.get_aggression():
            return 2
        else:
            return 1
    def get_closest_target(self, targets_set, start, world):
        q = Q.deque()
        q.append((start, 0))
        while q:
            location, distance = q.popleft()
            if distance > 2:
                break
            if location in targets_set:
                print("YAY")
                return location
            for tile in world.get_tiles_around(start).values():
                if not world.is_wall(tile.position):
                    q.append((tile.position, distance + 1))
                    
    def get_next_position(self, count, world, unit, defenders, state, helper):
        if unit.position in helper['targets']:
            helper['targets'].discard(unit.position)
        final_target = None
        if state.get_state() == "EXPAND":
            avoid = self.nests_list
        else:
            avoid = self.nests_list | set(defenders)

        if self.role == 3:
            if state.get_state() == "EXPAND":
                self.role = 1
            elif state.get_state() == "DESTROY":
                self.role = 2
            else:
                # Find if there are enemy tiles around it
                for tile in world.get_tiles_around(unit.position).values():
                    if tile.is_enemy():
                        return tile.position
                final_target = self.target
        if self.role == 1:
            # Find the closest priority target. 
            #if not self.target:
            # if the neighbour is an enemy, prioritize
            self.target = world.get_closest_capturable_tile_from(unit.position, avoid).position
            has_better_nest_creation = self.get_closest_target(helper['targets'], unit.position, world)
            if has_better_nest_creation:
                self.target = has_better_nest_creation
            closest_enemy = world.get_closest_enemy_from(unit.position, None)
            if world.get_shortest_path_distance(unit.position, closest_enemy.position) == 1:
                self.target = closest_enemy.position
            path = world.get_shortest_path(unit.position, self.target, avoid)
            #print("At: ", unit.position, " targeting ", self.target, " headed to ", path, ".. nest list = ", len(self.nests_list))
            if path:
                final_target = path[0]
        if self.role == 2:
            self.target = world.get_closest_enemy_nest_from(unit.position, avoid)
            path = world.get_shortest_path(unit.position, self.target, avoid)
            #print("DESTROYER At: ", unit.position, " targeting ", self.target, " headed to ", path, ".. nest list = ", len(self.nests_list))
            if path:
                final_target = path[0]
        if False:
        #if unit.position in defenders:
            closest_enemy_pos = world.get_closest_enemy_from(unit.position, None).position
            shortest_path = world.get_shortest_path(unit.position, closest_enemy_pos, None)
            if shortest_path and len(shortest_path) < 3:
                final_target = shortest_path[0]
                defenders[final_target] = defenders[unit.position][0]
                del defenders[unit.position]
                print("DEFENDER at ", unit.position, " moving to ", path[0])
            elif defenders[unit.position] != unit.position:
                path = world.get_shortest_path(unit.position, defenders[unit.position][0], None)
                if path:
                    final_target = path[0]
                    defenders[path[0]] = defenders[unit.position]
                    del defenders[unit.position]
                    print("DEFENDER at ", unit.position, " moving to ", path[0])
            elif unit.get_health() > 10:
                defenders[defenders[unit.position][1]] = defenders[unit.position]
                del defenders[unit.position]
                final_target = unit.position
        return final_target

class PlayerAI:
    def __init__(self):
        """
        Any instantiation code goes here
        """
        self.helpers = {}
        self.units_map = {}
        self.start_world = None
        self.actual_nests = None
        self.defenders = {}
        self.count = 0
        self.state = State()

    def add_neighbours(self, coor, my_set, world):
        for tile in world.get_tiles_around(coor).values():
            if world.is_wall(tile.position):
                continue
            my_set.add(tile.position)
        
    # Return a list of coordinates. To be called at the beginning.
    def decide_nest_positions(self, nest):
        self.helpers['adjacent_walls'] = HelperArray('adjacent_walls', self.start_world)
        self.helpers['targets'] = set()
        hypothetical_nests = [[0] * self.start_world.get_width() for _ in range(self.start_world.get_height())]
        hypothetical_nests[nest[1]][nest[0]] = 1
        self.add_neighbours(nest, self.helpers['targets'], self.start_world)
        num_nests = 1
        has_x_shape = True
        for x in range(nest[0] - 1, nest[0] + 2):
            for y in range(nest[1] - 1, nest[1] + 2):
                if self.start_world.is_wall(self.get_wrap_around_coordinate((x, y))):
                    has_x_shape = False
        if has_x_shape:
            for (x, y) in [(nest[0] - 1, nest[1] - 1), (nest[0] - 1, nest[1] + 1),
                           (nest[0] + 1, nest[1] - 1), (nest[0] + 1, nest[1] + 1)]:
                hypothetical_nests[y][x] = 1
                self.add_neighbours((x,y), self.helpers['targets'], self.start_world)
            num_nests = 4
        print_array(hypothetical_nests)
        
        visited = {}
        visited[nest] = True
        q = Q.deque()
        q.append((nest, 0))
        while q:
            location, distance = q.popleft()
            if distance > int(min(self.start_world.get_width(), self.start_world.get_height()) / 2.4):
                continue
            for coor in self.start_world.get_neighbours((location[0], location[1])).values():
                if coor not in visited:
                    q.append((coor, distance + 1))    
                visited[coor] = True
            # If it has an adjacent one with a better nest, forget about it.
            nest_func = self.can_be_nest_cluster
            if num_nests > 6:
                nest_func = self.can_be_nest
            if not self.has_better_adjacent_nest(location) and nest_func(location, hypothetical_nests):
                hypothetical_nests[location[1]][location[0]] = 1
                self.add_neighbours(location, self.helpers['targets'], self.start_world)
                num_nests += 1
        print_array(hypothetical_nests)
        print(self.helpers['targets'])
        return hypothetical_nests

    def get_wrap_around_coordinate(self, coor):
        return (coor[0] % self.start_world.get_width(), coor[1] % self.start_world.get_height())

    def can_be_nest_cluster(self, location, hypothetical_nests):
        x = location[0]
        y = location[1]
        for (i,j) in [(x-1, y-1), (x-1, y), (x-1, y+1),
                      (x, y-1), (x, y), (x, y+1),
                      (x+1, y-1), (x+1, y), (x+1, y+1)]:
            coor = self.get_wrap_around_coordinate((i, j))
            if hypothetical_nests[coor[1]][coor[0]]:
                return False
        return True

    def can_be_nest(self, location, hypothetical_nests):
        x = location[0]
        y = location[1]
        for (i,j) in [(x-2, y),
                      (x-1, y-1), (x-1, y), (x-1, y+1),
                      (x, y-2), (x, y-1), (x, y), (x, y+1), (x, y+2),
                      (x+1, y-1), (x+1, y), (x+1, y+1),
                      (x+2, y)]:
            coor = self.get_wrap_around_coordinate((i, j))
            if hypothetical_nests[coor[1]][coor[0]]:
                return False
        return True

    def has_better_adjacent_nest(self, location):
        priority = self.helpers['adjacent_walls'].help[location[1]][location[0]]
        x = location[0]
        y = location[1]
        for (i,j) in [(x-1, y-1), (x-1, y), (x-1, y+1),
                      (x, y-1), (x, y), (x, y+1),
                      (x+1, y-1), (x+1, y), (x+1, y+1)]:
            coor = self.get_wrap_around_coordinate((i, j))
            if self.helpers['adjacent_walls'].help[coor[1]][coor[0]] > priority:
                return True
        return False
            
    def first_run(self, world, nest):
        self.nest_list = set()
        self.nests = self.decide_nest_positions(nest.position)
        for y in range(world.get_height()):
            for x in range(world.get_width()):
                if self.nests[y][x]:
                    self.nest_list.add((x,y))
        # print(self.nest_list)

    def get_coor_from_rand(self, defender):
        if defender == 0:
            return (-1, -1)
        elif defender == 1:
            return (1, -1)
        elif defender == 2:
            return (1, 1)
        else:
            return (-1, 1)
        
    def do_move(self, world, friendly_units, enemy_units):
        self.count += 1
        if not self.start_world:
            self.start_world = world
            self.first_run(world, friendly_units[0])
        if not self.actual_nests:
            start_nest = friendly_units[0].position
            self.actual_nests = set()
            self.elite = set()
        if False:
        #if set(world.get_friendly_nest_positions()) != self.actual_nests:
            for nest in world.get_friendly_nest_positions():
                if nest not in self.actual_nests:
                    self.actual_nests.add(nest)
                    num_walls = self.helpers['adjacent_walls'].help[nest[1]][nest[0]]
                    # if nest has two or three walls, no need to have elite
                    if num_walls > 1:
                        break
                    # if nest has one wall or no walls, then we have elites
                    for x in range(self.state.get_defense()):
                        defender = random.randint(0, 3)
                        delta = self.get_coor_from_rand(defender)
                        defender_coor = self.get_wrap_around_coordinate((nest[0] + delta[0], nest[1] + delta[1]))
                        if world.is_wall(defender_coor):
                            continue
                        self.defenders[nest] = [defender_coor, nest]
                        print("GOT DEFENDER AT: ", defender_coor)
            
        for unit in friendly_units:
            if unit not in self.units_map:
                self.units_map[unit] = UnitMachine(unit, self.nest_list, world, self.defenders, self.state)
                #print("Could not find ", unit)
            #print("MY DEFENDERS at ", self.defenders)

            next_location = self.units_map[unit].get_next_position(self.count, world, unit, self.defenders, self.state, self.helpers)
            #print("Next_LOCATION: ", next_location)
            if next_location:
                world.move(unit, next_location)




