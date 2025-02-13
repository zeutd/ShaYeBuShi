package shayebushi.entities.units;

import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.content.Fx;
import mindustry.game.Team;
import mindustry.gen.Unit;
import mindustry.gen.UnitEntity;
import mindustry.graphics.Drawf;
import mindustry.world.draw.DrawDefault;

public class XianShangUnitEntity extends UnitEntity {
    public float miaoxianshang = 28000 ;
    public float dancixianshang = 3800 ;
    public float fenxianshang = 780000 ;
    public int i = 0 ;
    public int ii = 0 ;
    public float dangqianshanghaimiao = 0 ;
    public float dangqianshanghaifen = 0 ;
    public Team lastTeam = team ;
    public int timer = 0 ;
//    public float shangzhen = maxHealth;
    @Override
    public int classId() {
    return 113;
}
    @Override
    public void update(){
        health = Math.min(health, maxHealth) ;
        if (timer >= 600) {
            if (lastTeam != team) {
                team = lastTeam;
            }
        }
        else {
            lastTeam = team ;
        }
        timer ++ ;
//        if (shangzhen > health){
//            dangqianshanghaimiao += (shangzhen - health) ;
//            dangqianshanghaifen += (shangzhen - health) ;
//            if (dangqianshanghaimiao >= miaoxianshang || dangqianshanghaifen >= fenxianshang){
//                health += (shangzhen - health) ;
//            }
//        }
        i += Time.delta ;
        ii += Time.delta ;
        if (dangqianshanghaimiao >= miaoxianshang){
            dangqianshanghaimiao = miaoxianshang ;
        }
        if (dangqianshanghaifen >= fenxianshang){
            dangqianshanghaifen = fenxianshang ;
        }
        if (i >= Time.toSeconds) {
            i = 0 ;
            dangqianshanghaimiao = 0 ;
        }
        if (ii >= Time.toMinutes){
            ii = 0 ;
            dangqianshanghaifen = 0 ;
        }
        //if (!(this instanceof QiangZhiXianShangUnitEntity)) System.out.println(dangqianshanghaimiao + " " + dangqianshanghaifen + " " + miaoxianshang + " " + fenxianshang);
        //shangzhen = health ;
        lastTeam = team ;
        super.update();
    }
    @Override
    public void rawDamage(float a){
        if (dangqianshanghaimiao < miaoxianshang && dangqianshanghaifen < fenxianshang) {
            boolean hadShields = shield > 1.0E-4F;
            if (hadShields) {
                shieldAlpha = 1.0F;
            }
            float shieldDamage = Math.min(Math.max(shield, 0), a);
            shield -= shieldDamage;
            hitTime = 1.0F;
            a -= shieldDamage;
            //System.out.println("Before:" + a);
            a = Math.min(a, dancixianshang);
            //System.out.println("After:" + a);
            dangqianshanghaimiao += a;
            dangqianshanghaifen += a;
            if (a > 0 && type.killable) {
                health -= a;
                if (health <= 0 && !dead) {
                    kill();
                }
                if (hadShields && shield <= 1.0E-4F) {
                    Fx.unitShieldBreak.at(x, y, 0, team.color, this);
                }
            }
        }
        else {
            //this.heal(a) ;
        }
    }
    @Override
    public void draw(){
        super.draw();
        //Drawf.circles(this.x,this.y,this.hitSize * 1.15f,this.team.color);
    }
    @Override
    public void read(Reads read){
        super.read(read);
        dangqianshanghaifen = read.f() ;
        dangqianshanghaimiao = read.f() ;
        dancixianshang = read.f() ;
        miaoxianshang = read.f() ;
        fenxianshang = read.f() ;
    }
    @Override
    public void write(Writes writes){
        super.write(writes);
        writes.f(dangqianshanghaifen);
        writes.f(dangqianshanghaimiao);
        writes.f(dancixianshang);
        writes.f(miaoxianshang);
        writes.f(fenxianshang);
    }
}
