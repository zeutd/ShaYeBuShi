package shayebushi.world.blocks.defense.turrets;

import arc.Core;
import arc.Events;
import arc.audio.Sound;
import arc.func.Boolf;
import arc.graphics.Color;
import arc.graphics.g2d.TextureRegion;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.struct.EnumSet;
import arc.struct.Seq;
import arc.util.Nullable;
import arc.util.Time;
import arc.util.Tmp;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.content.Fx;
import mindustry.content.UnitTypes;
import mindustry.core.World;
import mindustry.entities.*;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.pattern.ShootPattern;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.graphics.Drawf;
import mindustry.graphics.Pal;
import mindustry.logic.LAccess;
import mindustry.logic.Ranged;
import mindustry.type.Liquid;
import mindustry.ui.Bar;
import mindustry.world.blocks.ControlBlock;
import mindustry.world.blocks.defense.turrets.ReloadTurret;
import mindustry.world.blocks.defense.turrets.Turret;
import mindustry.world.consumers.ConsumeCoolant;
import mindustry.world.consumers.ConsumeLiquidBase;
import mindustry.world.consumers.ConsumeLiquidFilter;
import mindustry.world.draw.DrawBlock;
import mindustry.world.draw.DrawTurret;
import mindustry.world.meta.*;
import shayebushi.world.blocks.AbilityBlock;
import shayebushi.world.blocks.abilities.BlockAbility;
import shayebushi.world.blocks.abilities.TurretAbility;
import shayebushi.world.draw.DrawAbilityTurret;

import static mindustry.Vars.state;
import static mindustry.Vars.tilesize;

public class AbilityTurret extends AbilityBlock {
    public float range = 80f;
    public float placeOverlapMargin = 8 * 7f;
    public float rotateSpeed = 5;
    public float fogRadiusMultiplier = 1f;

    /** Effect displayed when coolant is used. */
    public Effect coolEffect = Fx.fuelburn;
    /** How much reload is lowered by for each unit of liquid of heat capacity. */
    public float coolantMultiplier = 5f;
    /** If not null, this consumer will be used for coolant. */
    public @Nullable
    ConsumeLiquidBase coolant;
    public float reload = 10f;
    //after being logic-controlled and this amount of time passes, the turret will resume normal AI
    public final static float logicControlCooldown = 60 * 2;

    public final int timerTarget = timers++;
    /** Ticks between attempt at finding a target. */
    public float targetInterval = 20;

    /** Maximum ammo units stored. */
    public int maxAmmo = 30;
    /** Ammo units used per shot. */
    public int ammoPerShot = 1;
    /** If true, ammo is only consumed once per shot regardless of bullet count. */
    public boolean consumeAmmoOnce = true;
    /** Minimum input heat required to fire. */
    public float heatRequirement = -1f;
    /** Maximum efficiency possible, if this turret uses heat. */
    public float maxHeatEfficiency = 3f;

    /** Bullet angle randomness in degrees. */
    public float inaccuracy = 0f;
    /** Fraction of bullet velocity that is random. */
    public float velocityRnd = 0f;
    /** Maximum angle difference in degrees at which turret will still try to shoot. */
    public float shootCone = 8f;
    /** Turret shoot point. */
    public float shootX = 0f, shootY = Float.NEGATIVE_INFINITY;
    /** Random spread on the X axis. */
    public float xRand = 0f;
    /** Minimum bullet range. Used for artillery only. */
    public float minRange = 0f;
    /** Minimum warmup needed to fire. */
    public float minWarmup = 0f;
    /** If true, this turret will accurately target moving targets with respect to charge time. */
    public boolean accurateDelay = true;
    /** If false, this turret can't move while charging. */
    public boolean moveWhileCharging = true;
    /** How long warmup is maintained even if this turret isn't shooting. */
    public float warmupMaintainTime = 0f;
    /** pattern used for bullets */
    public ShootPattern shoot = new ShootPattern();

    /** If true, this block targets air units. */
    public boolean targetAir = true;
    /** If true, this block targets ground units and structures. */
    public boolean targetGround = true;
    /** If true, this block targets friend blocks, to heal them. */
    public boolean targetHealing = false;
    /** If true, this turret can be controlled by players. */
    public boolean playerControllable = true;
    /** If true, this block will display ammo multipliers in its stats (irrelevant for certain types of turrets). */
    public boolean displayAmmoMultiplier = true;
    /** If false, 'under' blocks like conveyors are not targeted. */
    public boolean targetUnderBlocks = true;
    /** If true, the turret will always shoot when it has ammo, regardless of targets in range or any control. */
    public boolean alwaysShooting = false;
    /** Whether this turret predicts unit movement. */
    public boolean predictTarget = true;
    /** Function for choosing which unit to target. */
    public Units.Sortf unitSort = UnitSorts.closest;
    /** Filter for types of units to attack. */
    public Boolf<Unit> unitFilter = u -> true;
    /** Filter for types of buildings to attack. */
    public Boolf<Building> buildingFilter = b -> targetUnderBlocks || !b.block.underBullets;

    /** Color of heat region drawn on top (if found) */
    public Color heatColor = Pal.turretHeat;
    /** Optional override for all shoot effects. */
    public @Nullable
    Effect shootEffect;
    /** Optional override for all smoke effects. */
    public @Nullable Effect smokeEffect;
    /** Effect created when ammo is used. Not optional. */
    public Effect ammoUseEffect = Fx.none;
    /** Sound emitted when a single bullet is shot. */
    public Sound shootSound = Sounds.shoot;
    /** Sound emitted when shoot.firstShotDelay is >0 and shooting begins. */
    public Sound chargeSound = Sounds.none;
    /** Range for pitch of shoot sound. */
    public float soundPitchMin = 0.9f, soundPitchMax = 1.1f;
    /** Backwards Y offset of ammo eject effect. */
    public float ammoEjectBack = 1f;
    /** Lerp speed of turret warmup. */
    public float shootWarmupSpeed = 0.1f;
    /** If true, turret warmup is linear instead of a curve. */
    public boolean linearWarmup = false;
    /** Visual amount by which the turret recoils back per shot. */
    public float recoil = 1f;
    /** Number of additional counters for recoil. */
    public int recoils = -1;
    /** ticks taken for turret to return to starting position in ticks. uses reload time by default  */
    public float recoilTime = -1f;
    /** power curve applied to visual recoil */
    public float recoilPow = 1.8f;
    /** ticks to cool down the heat region */
    public float cooldownTime = 20f;
    /** Visual elevation of turret shadow, -1 to use defaults. */
    public float elevation = -1f;
    /** How much the screen shakes per shot. */
    public float shake = 0f;

    /** Defines drawing behavior for this turret. */
    public DrawBlock drawer = new DrawAbilityTurret(){

    };

    public AbilityTurret(String name){
        super(name);

        update = true;
        solid = true;
        outlineIcon = true;
        attacks = true;
        priority = TargetPriority.turret;
        group = BlockGroup.turrets;
        flags = EnumSet.of(BlockFlag.turret);
        liquidCapacity = 20f;
        quickRotate = false;
        outlinedIcon = 1;
    }

    @Override
    public boolean outputsItems(){
        return false;
    }

    @Override
    public void setStats(){
        super.setStats();
        stats.add(Stat.shootRange, range / tilesize, StatUnit.blocks);
        if(coolant != null){
            stats.remove(Stat.booster);
            stats.add(Stat.booster, StatValues.boosters(reload, coolant.amount, coolantMultiplier, true, l -> l.coolant && consumesLiquid(l)));
        }
        stats.add(Stat.inaccuracy, (int)inaccuracy, StatUnit.degrees);
        stats.add(Stat.reload, 60f / (reload) * shoot.shots, StatUnit.perSecond);
        stats.add(Stat.targetsAir, targetAir);
        stats.add(Stat.targetsGround, targetGround);
        if(ammoPerShot != 1) stats.add(Stat.ammoUse, ammoPerShot, StatUnit.perShot);
        if(heatRequirement > 0) stats.add(Stat.input, heatRequirement, StatUnit.heatUnits);
    }

    @Override
    public void setBars(){
        super.setBars();

        if(heatRequirement > 0){
            addBar("heat", (Turret.TurretBuild entity) ->
                    new Bar(() ->
                            Core.bundle.format("bar.heatpercent", (int)entity.heatReq, (int)(Math.min(entity.heatReq / heatRequirement, maxHeatEfficiency) * 100)),
                            () -> Pal.lightOrange,
                            () -> entity.heatReq / heatRequirement));
        }
    }

    @Override
    public void init(){
        if(shootY == Float.NEGATIVE_INFINITY) shootY = size * tilesize / 2f;
        if(elevation < 0) elevation = size / 2f;
        if(recoilTime < 0f) recoilTime = reload;
        if(cooldownTime < 0f) cooldownTime = reload;
        if(coolant == null){
            coolant = findConsumer(c -> c instanceof ConsumeCoolant);
        }

        //just makes things a little more convenient
        if(coolant != null){
            //TODO coolant fix
            coolant.update = false;
            coolant.booster = true;
            coolant.optional = true;

            //json parsing does not add to consumes
            if(!hasConsumer(coolant)) consume(coolant);
        }

        placeOverlapRange = Math.max(placeOverlapRange, range + placeOverlapMargin);
        fogRadius = Math.max(Mathf.round(range / tilesize * fogRadiusMultiplier), fogRadius);
        super.init();
    }
    @Override
    public void drawPlace(int x, int y, int rotation, boolean valid){
        super.drawPlace(x, y, rotation, valid);

        Drawf.dashCircle(x * tilesize + offset, y * tilesize + offset, range, Pal.placing);

        if(fogRadiusMultiplier < 0.99f && state.rules.fog){
            Drawf.dashCircle(x * tilesize + offset, y * tilesize + offset, range * fogRadiusMultiplier, Pal.lightishGray);
        }
    }

    @Override
    public void load(){
        super.load();

        drawer.load(this);
    }

    @Override
    public TextureRegion[] icons(){
        return drawer.finalIcons(this);
    }

    @Override
    public void getRegionsToOutline(Seq<TextureRegion> out){
        drawer.getRegionsToOutline(this, out);
    }

    public void limitRange(BulletType bullet, float margin){
        float realRange = bullet.rangeChange + range;
        //doesn't handle drag
        bullet.lifetime = (realRange + margin) / bullet.speed;
    }

    public static abstract class AmmoEntry{
        public int amount;

        public abstract BulletType type();
    }

    public class AbilityTurretBuild extends AbilityBuild implements ControlBlock, Ranged {
        //TODO storing these as instance variables is horrible design
        /** Turret sprite offset, based on recoil. Updated every frame. */
        public Vec2 recoilOffset = new Vec2();

        public Seq<Turret.AmmoEntry> ammo = new Seq<>();
        public int totalAmmo;
        public float curRecoil, heat, logicControlTime = -1;
        public @Nullable float[] curRecoils;
        public float shootWarmup, charge, warmupHold = 0f;
        public int totalShots, barrelCounter;
        public boolean logicShooting = false;
        public @Nullable
        Posc target;
        public Vec2 targetPos = new Vec2();
        public BlockUnitc unit = (BlockUnitc) UnitTypes.block.create(team);
        public boolean wasShooting;
        public int queuedBullets = 0;

        public float heatReq;
        public float[] sideHeat = new float[4];
        public float rotation = 90;
        public float reloadCounter;

        protected void updateCooling(){
            if(reloadCounter < reload && coolant != null && coolant.efficiency(this) > 0 && efficiency > 0){
                float capacity = coolant instanceof ConsumeLiquidFilter filter ? filter.getConsumed(this).heatCapacity : 1f;
                float amount = coolant.amount * coolant.efficiency(this);
                coolant.update(this);
                reloadCounter += amount * edelta() * capacity * coolantMultiplier;

                if(Mathf.chance(0.06 * amount)){
                    coolEffect.at(x + Mathf.range(size * tilesize / 2f), y + Mathf.range(size * tilesize / 2f));
                }
            }
        }

        protected float baseReloadSpeed(){
            return efficiency;
        }

        @Override
        public void drawSelect(){
            Drawf.dashCircle(x, y, range(), team.color);
        }

        public float estimateDps(){
            if(!hasAmmo()) return 0f;
            return shoot.shots / reload * 60f * (peekAmmo() == null ? 0f : peekAmmo().estimateDPS()) * potentialEfficiency * timeScale;
        }

        public float range(){
            if(peekAmmo() != null){
                return range + peekAmmo().rangeChange;
            }
            return range;
        }

        @Override
        public float warmup(){
            return shootWarmup;
        }

        @Override
        public float drawrot(){
            return rotation - 90;
        }

        @Override
        public boolean shouldConsume(){
            return isShooting() || reloadCounter < reload;
        }

        @Override
        public boolean canControl(){
            return playerControllable;
        }

        @Override
        public void control(LAccess type, double p1, double p2, double p3, double p4){
            if(type == LAccess.shoot && !unit.isPlayer()){
                targetPos.set(World.unconv((float)p1), World.unconv((float)p2));
                logicControlTime = logicControlCooldown;
                logicShooting = !Mathf.zero(p3);
            }

            super.control(type, p1, p2, p3, p4);
        }

        @Override
        public void control(LAccess type, Object p1, double p2, double p3, double p4){
            if(type == LAccess.shootp && (unit == null || !unit.isPlayer())){
                logicControlTime = logicControlCooldown;
                logicShooting = !Mathf.zero(p2);

                if(p1 instanceof Posc pos){
                    targetPosition(pos);
                }
            }

            super.control(type, p1, p2, p3, p4);
        }

        @Override
        public double sense(LAccess sensor){
            return switch(sensor){
                case ammo -> totalAmmo;
                case ammoCapacity -> maxAmmo;
                case rotation -> rotation;
                case shootX -> World.conv(targetPos.x);
                case shootY -> World.conv(targetPos.y);
                case shooting -> isShooting() ? 1 : 0;
                case progress -> progress();
                default -> super.sense(sensor);
            };
        }

        @Override
        public float progress(){
            return Mathf.clamp(reloadCounter / reload);
        }

        public boolean isShooting(){
            return alwaysShooting || (isControlled() ? unit.isShooting() : logicControlled() ? logicShooting : target != null);
        }

        @Override
        public Unit unit(){
            //make sure stats are correct
            unit.tile(this);
            unit.team(team);
            return (Unit)unit;
        }

        public boolean logicControlled(){
            return logicControlTime > 0;
        }

        public boolean isActive(){
            return (target != null || wasShooting) && enabled;
        }

        public void targetPosition(Posc pos){
            if(!hasAmmo() || pos == null) return;
            BulletType bullet = peekAmmo();

            var offset = Tmp.v1.setZero();

            //when delay is accurate, assume unit has moved by chargeTime already
            if(accurateDelay && !moveWhileCharging && pos instanceof Hitboxc h){
                offset.set(h.deltaX(), h.deltaY()).scl(shoot.firstShotDelay / Time.delta);
            }

            if(predictTarget){
                targetPos.set(Predict.intercept(this, pos, offset.x, offset.y, bullet.speed <= 0.01f ? 99999999f : bullet.speed));
            }else{
                targetPos.set(pos);
            }

            if(targetPos.isZero()){
                targetPos.set(pos);
            }
        }

        @Override
        public void draw(){
            drawer.draw(this);
            for (BlockAbility ba : as) {
                ba.draw(this);
            }
        }

        @Override
        public void updateTile(){
            super.updateTile();
            if(!validateTarget()) target = null;

            float warmupTarget = (isShooting() && canConsume()) || charging() ? 1f : 0f;
            if(warmupTarget > 0 && shootWarmup >= minWarmup && !isControlled()){
                warmupHold = 1f;
            }
            if(warmupHold > 0f){
                warmupHold -= Time.delta / warmupMaintainTime;
                warmupTarget = 1f;
            }

            if(linearWarmup){
                shootWarmup = Mathf.approachDelta(shootWarmup, warmupTarget, shootWarmupSpeed * (warmupTarget > 0 ? efficiency : 1f));
            }else{
                shootWarmup = Mathf.lerpDelta(shootWarmup, warmupTarget, shootWarmupSpeed * (warmupTarget > 0 ? efficiency : 1f));
            }

            wasShooting = false;

            curRecoil = Mathf.approachDelta(curRecoil, 0, 1 / recoilTime);
            if(recoils > 0){
                if(curRecoils == null) curRecoils = new float[recoils];
                for(int i = 0; i < recoils; i++){
                    curRecoils[i] = Mathf.approachDelta(curRecoils[i], 0, 1 / recoilTime);
                }
            }
            heat = Mathf.approachDelta(heat, 0, 1 / cooldownTime);
            charge = charging() ? Mathf.approachDelta(charge, 1, 1 / shoot.firstShotDelay) : 0;

            unit.tile(this);
            unit.rotation(rotation);
            unit.team(team);
            recoilOffset.trns(rotation, -Mathf.pow(curRecoil, recoilPow) * recoil);

            if(logicControlTime > 0){
                logicControlTime -= Time.delta;
            }

            if(heatRequirement > 0){
                heatReq = calculateHeat(sideHeat);
            }

            //turret always reloads regardless of whether it's targeting something
            updateReload();

            if(hasAmmo()){
                if(Float.isNaN(reloadCounter)) reloadCounter = 0;

                if(timer(timerTarget, targetInterval)){
                    findTarget();
                }

                if(validateTarget()){
                    boolean canShoot = true;

                    if(isControlled()){ //player behavior
                        targetPos.set(unit.aimX(), unit.aimY());
                        canShoot = unit.isShooting();
                    }else if(logicControlled()){ //logic behavior
                        canShoot = logicShooting;
                    }else{ //default AI behavior
                        targetPosition(target);

                        if(Float.isNaN(rotation)) rotation = 0;
                    }

                    if(!isControlled()){
                        unit.aimX(targetPos.x);
                        unit.aimY(targetPos.y);
                    }

                    float targetRot = angleTo(targetPos);

                    if(shouldTurn()){
                        turnToTarget(targetRot);
                    }

                    if(!alwaysShooting && Angles.angleDist(rotation, targetRot) < shootCone && canShoot){
                        wasShooting = true;
                        updateShooting();
                    }
                }

                if(alwaysShooting){
                    wasShooting = true;
                    updateShooting();
                }
            }

            if(coolant != null){
                updateCooling();
            }
        }

        @Override
        public void handleLiquid(Building source, Liquid liquid, float amount){
            if(coolant != null && liquids.currentAmount() <= 0.001f){
                Events.fire(EventType.Trigger.turretCool);
            }

            super.handleLiquid(source, liquid, amount);
        }

        protected boolean validateTarget(){
            return !Units.invalidateTarget(target, canHeal() ? Team.derelict : team, x, y) || isControlled() || logicControlled();
        }

        protected boolean canHeal(){
            return targetHealing && hasAmmo() && peekAmmo().collidesTeam && peekAmmo().heals();
        }

        protected void findTarget(){
            float range = range();

            if(targetAir && !targetGround){
                target = Units.bestEnemy(team, x, y, range, e -> !e.dead() && !e.isGrounded() && unitFilter.get(e), unitSort);
            }else{
                target = Units.bestTarget(team, x, y, range, e -> !e.dead() && unitFilter.get(e) && (e.isGrounded() || targetAir) && (!e.isGrounded() || targetGround), b -> targetGround && buildingFilter.get(b), unitSort);
            }

            if(target == null && canHeal()){
                target = Units.findAllyTile(team, x, y, range, b -> b.damaged() && b != this);
            }
        }

        protected void turnToTarget(float targetRot){
            rotation = Angles.moveToward(rotation, targetRot, rotateSpeed * delta() * potentialEfficiency);
        }

        public boolean shouldTurn(){
            return moveWhileCharging || !charging();
        }

        @Override
        public void updateEfficiencyMultiplier(){
            if(heatRequirement > 0){
                efficiency *= Math.min(Math.max(heatReq / heatRequirement, cheating() ? 1f : 0f), maxHeatEfficiency);
            }
        }

        /** Consume ammo and return a type. */
        public BulletType useAmmo(){
            if(cheating()) return peekAmmo();

            Turret.AmmoEntry entry = ammo.peek();
            entry.amount -= ammoPerShot;
            if(entry.amount <= 0) ammo.pop();
            totalAmmo -= ammoPerShot;
            totalAmmo = Math.max(totalAmmo, 0);
            return entry.type();
        }

        /** @return the ammo type that will be returned if useAmmo is called. */
        public @Nullable BulletType peekAmmo(){
            return ammo.size == 0 ? null : ammo.peek().type();
        }

        /** @return whether the turret has ammo. */
        public boolean hasAmmo(){
            //used for "side-ammo" like gas in some turrets
            if(!canConsume()) return false;

            //skip first entry if it has less than the required amount of ammo
            if(ammo.size >= 2 && ammo.peek().amount < ammoPerShot && ammo.get(ammo.size - 2).amount >= ammoPerShot){
                ammo.swap(ammo.size - 1, ammo.size - 2);
            }

            return ammo.size > 0 && (ammo.peek().amount >= ammoPerShot || cheating());
        }

        public boolean charging(){
            return queuedBullets > 0 && shoot.firstShotDelay > 0;
        }

        protected void updateReload(){
            float multiplier = hasAmmo() ? peekAmmo().reloadMultiplier : 1f;
            reloadCounter += delta() * multiplier * baseReloadSpeed();

            //cap reload for visual reasons
            reloadCounter = Math.min(reloadCounter, reload);
        }

        protected void updateShooting(){

            if(reloadCounter >= reload && !charging() && shootWarmup >= minWarmup){
                BulletType type = peekAmmo();

                shoot(type);

                reloadCounter %= reload;
            }
        }

        protected void shoot(BulletType type){
            float
                    bulletX = x + Angles.trnsx(rotation - 90, shootX, shootY),
                    bulletY = y + Angles.trnsy(rotation - 90, shootX, shootY);

            if(shoot.firstShotDelay > 0){
                chargeSound.at(bulletX, bulletY, Mathf.random(soundPitchMin, soundPitchMax));
                type.chargeEffect.at(bulletX, bulletY, rotation);
            }

            shoot.shoot(barrelCounter, (xOffset, yOffset, angle, delay, mover) -> {
                queuedBullets++;
                if(delay > 0f){
                    Time.run(delay, () -> bullet(type, xOffset, yOffset, angle, mover));
                }else{
                    bullet(type, xOffset, yOffset, angle, mover);
                }
            }, () -> barrelCounter++);

            if(consumeAmmoOnce){
                useAmmo();
            }
        }

        protected void bullet(BulletType type, float xOffset, float yOffset, float angleOffset, Mover mover){
            queuedBullets --;

            if(dead || (!consumeAmmoOnce && !hasAmmo())) return;

            float
                    xSpread = Mathf.range(xRand),
                    bulletX = x + Angles.trnsx(rotation - 90, shootX + xOffset + xSpread, shootY + yOffset),
                    bulletY = y + Angles.trnsy(rotation - 90, shootX + xOffset + xSpread, shootY + yOffset),
                    shootAngle = rotation + angleOffset + Mathf.range(inaccuracy + type.inaccuracy);

            float lifeScl = type.scaleLife ? Mathf.clamp(Mathf.dst(bulletX, bulletY, targetPos.x, targetPos.y) / type.range, minRange / type.range, range() / type.range) : 1f;

            Bullet b = type.create(this, team, bulletX, bulletY, shootAngle, -1f, (1f - velocityRnd) + Mathf.random(velocityRnd), lifeScl, null, mover, targetPos.x, targetPos.y) ;
            handleBullet(b, xOffset, yOffset, shootAngle - rotation);

            (shootEffect == null ? type.shootEffect : shootEffect).at(bulletX, bulletY, rotation + angleOffset, type.hitColor);
            (smokeEffect == null ? type.smokeEffect : smokeEffect).at(bulletX, bulletY, rotation + angleOffset, type.hitColor);
            shootSound.at(bulletX, bulletY, Mathf.random(soundPitchMin, soundPitchMax));

            ammoUseEffect.at(
                    x - Angles.trnsx(rotation, ammoEjectBack),
                    y - Angles.trnsy(rotation, ammoEjectBack),
                    rotation * Mathf.sign(xOffset)
            );

            if(shake > 0){
                Effect.shake(shake, shake, this);
            }

            curRecoil = 1f;
            if(recoils > 0){
                curRecoils[barrelCounter % recoils] = 1f;
            }
            heat = 1f;
            totalShots++;

            if(!consumeAmmoOnce){
                useAmmo();
            }

            for (BlockAbility ba : as) {
                if (ba instanceof TurretAbility ta) {
                    ta.onShoot(this, b) ;
                }
            }
        }

        protected void handleBullet(@Nullable Bullet bullet, float offsetX, float offsetY, float angleOffset){

        }

        @Override
        public float activeSoundVolume(){
            return shootWarmup;
        }

        @Override
        public boolean shouldActiveSound(){
            return shootWarmup > 0.01f && loopSound != Sounds.none;
        }

        @Override
        public void write(Writes write){
            super.write(write);
            write.f(reloadCounter);
            write.f(rotation);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);

            if(revision >= 1){
                reloadCounter = read.f();
                rotation = read.f();
            }
        }

        @Override
        public byte version(){
            return 1;
        }
    }

    public static class BulletEntry{
        public Bullet bullet;
        public float x, y, rotation, life;

        public BulletEntry(Bullet bullet, float x, float y, float rotation, float life){
            this.bullet = bullet;
            this.x = x;
            this.y = y;
            this.rotation = rotation;
            this.life = life;
        }
    }
}
