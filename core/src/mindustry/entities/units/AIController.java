package mindustry.entities.units;

import arc.math.*;
import arc.math.geom.*;
import arc.util.*;
import mindustry.entities.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.meta.*;

import static mindustry.Vars.*;

public class AIController implements UnitController{
    protected static final Vec2 vec = new Vec2();
    protected static final int timerTarget = 0;

    protected Unit unit;
    protected Interval timer = new Interval(4);

    /** main target that is being faced */
    protected Teamc target;
    /** targets for each weapon */
    protected Teamc[] targets = {};

    {
        timer.reset(0, Mathf.random(40f));
    }

    @Override
    public void updateUnit(){
        updateTargeting();
        updateMovement();
    }

    protected void updateMovement(){

    }

    protected void updateTargeting(){
        if(unit.hasWeapons()){

            updateWeapons();
        }
    }

    protected void updateWeapons(){
        if(targets.length != unit.mounts.length) targets = new Teamc[unit.mounts.length];

        float rotation = unit.rotation - 90;
        boolean ret = retarget();

        if(ret){
            target = findTarget(unit.x, unit.y, unit.range(), unit.type().targetAir, unit.type().targetGround);
        }

        if(Units.invalidateTarget(target, unit.team, unit.x, unit.y)){
            target = null;
        }

        for(int i = 0; i < targets.length; i++){
            WeaponMount mount = unit.mounts[i];
            Weapon weapon = mount.weapon;

            float mountX = unit.x + Angles.trnsx(rotation, weapon.x, weapon.y),
                mountY = unit.y + Angles.trnsy(rotation, weapon.x, weapon.y);

            if(unit.type().singleTarget){
                targets[i] = target;
            }else{
                if(ret){
                    targets[i] = findTarget(mountX, mountY, weapon.bullet.range(), weapon.bullet.collidesAir, weapon.bullet.collidesGround);
                }

                if(Units.invalidateTarget(targets[i], unit.team, mountX, mountY, weapon.bullet.range())){
                    targets[i] = null;
                }
            }

            boolean shoot = false;

            if(targets[i] != null){
                shoot = targets[i].within(mountX, mountY, weapon.bullet.range());

                if(shoot){
                    Vec2 to = Predict.intercept(unit, targets[i], weapon.bullet.speed);
                    mount.aimX = to.x;
                    mount.aimY = to.y;
                }
            }

            mount.shoot = shoot;
            mount.rotate = shoot;
        }
    }

    protected Teamc targetFlag(float x, float y, BlockFlag flag, boolean enemy){
        Tile target = Geometry.findClosest(x, y, enemy ? indexer.getEnemy(unit.team, flag) : indexer.getAllied(unit.team, flag));
        return target == null ? null : target.build;
    }

    protected Teamc target(float x, float y, float range, boolean air, boolean ground){
        return Units.closestTarget(unit.team, x, y, range, u -> u.checkTarget(air, ground), t -> ground);
    }

    protected boolean retarget(){
        return timer.get(timerTarget, 30);
    }

    protected Teamc findTarget(float x, float y, float range, boolean air, boolean ground){
        return target(x, y, range, air, ground);
    }

    protected void init(){

    }

    @Override
    public void unit(Unit unit){
        if(this.unit == unit) return;

        this.unit = unit;
        init();
    }

    @Override
    public Unit unit(){
        return unit;
    }
}
