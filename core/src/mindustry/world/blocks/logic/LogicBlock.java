package mindustry.world.blocks.logic;

import arc.func.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.io.*;
import mindustry.logic.*;
import mindustry.logic.LAssembler.*;
import mindustry.logic.LExecutor.*;
import mindustry.ui.*;
import mindustry.world.*;

import static mindustry.Vars.*;

public class LogicBlock extends Block{
    private static final IntSeq removal = new IntSeq();

    public int maxInstructionScale = 8;
    public int instructionsPerTick = 1;
    public float range = 8 * 10;
    public int memory = 16;

    public LogicBlock(String name){
        super(name);
        update = true;
        configurable = true;

        config(String.class, (LogicEntity entity, String code) -> entity.updateCode(code));

        config(Integer.class, (LogicEntity entity, Integer pos) -> {
            if(entity.connections.contains(pos)){
                entity.connections.removeValue(pos);
            }else{
                entity.connections.add(pos);
            }

            entity.updateCode(entity.code);
        });
    }

    public class LogicEntity extends Building{
        /** logic "source code" as list of asm statements */
        public String code = "";
        public LExecutor executor = new LExecutor();
        public float accumulator = 0;
        public IntSeq connections = new IntSeq();
        public boolean loaded = false;

        public LogicEntity(){
            executor.memory = new double[memory];
        }

        public void updateCode(String str){
            updateCode(str, null);
        }

        public void updateCode(String str, Cons<LAssembler> assemble){
            if(str != null){
                code = str;

                try{
                    //create assembler to store extra variables
                    LAssembler asm = LAssembler.assemble(str);

                    //store connections
                    for(int i = 0; i < connections.size; i++){
                        asm.putConst("@" + i, world.build(connections.get(i)));
                    }

                    //store any older variables
                    for(Var var : executor.vars){
                        if(!var.constant){
                            BVar dest = asm.getVar(var.name);
                            if(dest != null && !dest.constant){
                                dest.value = var.isobj ? var.objval : var.numval;
                            }
                        }
                    }

                    //inject any extra variables
                    if(assemble != null){
                        assemble.get(asm);
                    }

                    asm.putConst("@this", this);

                    executor.load(asm);
                }catch(Exception e){
                    e.printStackTrace();

                    //handle malformed code and replace it with nothing
                    executor.load("");
                }
            }
        }

        @Override
        public void onProximityUpdate(){
            super.onProximityUpdate();

            if(!loaded){
                //properly fetches connections
                updateCode(code);
                loaded = true;
            }
        }

        @Override
        public void updateTile(){
            //remove invalid links
            //TODO remove variables
            removal.clear();

            for(int i = 0; i < connections.size; i++){
                int val = connections.get(i);
                if(!validLink(val)){
                    removal.add(val);
                }
            }

            if(!removal.isEmpty()){
                updateCode(code);
            }

            connections.removeAll(removal);

            accumulator += edelta() * instructionsPerTick;

            if(accumulator > maxInstructionScale * instructionsPerTick) accumulator = maxInstructionScale * instructionsPerTick;

            for(int i = 0; i < (int)accumulator; i++){
                if(executor.initialized()){
                    executor.runOnce();
                }
                accumulator --;
            }
        }

        @Override
        public String config(){
            return code;
        }

        @Override
        public void drawConfigure(){
            super.drawConfigure();

            Drawf.circles(x, y, range);

            for(int i = 0; i < connections.size; i++){
                int pos = connections.get(i);

                if(validLink(pos)){
                    Building other = Vars.world.build(pos);
                    Drawf.square(other.x, other.y, other.block.size * tilesize / 2f + 1f, Pal.place);
                }
            }

            //draw top text on separate layer
            for(int i = 0; i < connections.size; i++){
                int pos = connections.get(i);

                if(validLink(pos)){
                    Building other = Vars.world.build(pos);
                    other.block.drawPlaceText("@" + i, other.tileX(), other.tileY(), true);
                }
            }
        }

        public boolean validLink(int pos){
            Building other = Vars.world.build(pos);
            return other != null && other.team == team && other.within(this, range + other.block.size*tilesize/2f);
        }

        @Override
        public void drawSelect(){

        }

        @Override
        public void buildConfiguration(Table table){
            Table cont = new Table();
            cont.defaults().size(40);

            cont.button(Icon.pencil, Styles.clearTransi, () -> {
                Vars.ui.logic.show(code, this::configure);
            });

            //cont.button(Icon.refreshSmall, Styles.clearTransi, () -> {

            //});

            table.add(cont);
        }

        @Override
        public boolean onConfigureTileTapped(Building other){
            if(this == other){
                return true;
            }

            if(validLink(other.pos())){
                configure(other.pos());
                return false;
            }

            return super.onConfigureTileTapped(other);
        }

        @Override
        public void write(Writes write){
            super.write(write);

            write.str(code);
            write.s(connections.size);
            for(int i = 0; i < connections.size; i++){
                write.i(connections.get(i));
            }

            //write only the non-constant variables
            int count = Structs.count(executor.vars, v -> !v.constant);

            write.i(count);
            for(int i = 0; i < executor.vars.length; i++){
                Var v = executor.vars[i];

                if(v.constant) continue;

                //write the name and the object value
                write.str(v.name);

                Object value = v.isobj ? v.objval : v.numval;
                TypeIO.writeObject(write, value);
            }

            write.i(executor.memory.length);
            for(int i = 0; i < executor.memory.length; i++){
                write.d(executor.memory[i]);
            }
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);

            code = read.str();
            connections.clear();
            short total = read.s();
            for(int i = 0; i < total; i++){
                connections.add(read.i());
            }

            int varcount = read.i();

            //variables need to be temporarily stored in an array until they can be used
            String[] names = new String[varcount];
            Object[] values = new Object[varcount];

            for(int i = 0; i < varcount; i++){
                String name = read.str();
                Object value = TypeIO.readObject(read);

                names[i] = name;
                values[i] = value;
            }

            int memory = read.i();
            double[] memorybank = executor.memory.length != memory ? new double[memory] : executor.memory;
            for(int i = 0; i < memory; i++){
                memorybank[i] = read.d();
            }

            updateCode(code, asm -> {

                //load up the variables that were stored
                for(int i = 0; i < varcount; i++){
                    BVar dest = asm.getVar(names[i]);
                    if(dest != null && !dest.constant){
                        dest.value = values[i];
                    }
                }
            });

            executor.memory = memorybank;
        }
    }
}
