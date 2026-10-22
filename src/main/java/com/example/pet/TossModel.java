package com.example.pet;

/** 简化二维刚体平移：重力、空气阻力、非完全弹性碰撞、地面动摩擦。 */
final class TossModel {
    static final double GRAVITY = 1850; // 像素/秒²；屏幕坐标向下为正
    private static final double AIR_DRAG = .06;
    private static final double RESTITUTION = .60;
    private static final double FLOOR_FRICTION = .22 * GRAVITY;
    double x, y, vx, vy;
    private final double minX, minY, maxX, maxY;
    private boolean grounded;

    TossModel(double x, double y, double vx, double vy, double minX, double minY, double maxX, double maxY) {
        this.minX = minX; this.minY = minY;
        this.maxX = Math.max(minX, maxX); this.maxY = Math.max(minY, maxY);
        this.x = Math.max(minX, Math.min(this.maxX, x));
        this.y = Math.max(minY, Math.min(this.maxY, y));
        double speed = Math.hypot(vx, vy);
        double limit = speed > 2200 ? 2200 / speed : 1;
        this.vx = vx * limit; this.vy = vy * limit;
        boolean onFloor = this.y >= this.maxY - .001;
        grounded = onFloor && this.vy >= 0 && this.vy < 70;
        if (grounded) this.vy = 0;
        else if (onFloor && this.vy >= 70) { this.vy = -this.vy * RESTITUTION; this.vx *= .86; }
    }
    boolean step(double dt) {
        if (!Double.isFinite(dt) || dt <= 0) return false;
        double elapsed = Math.min(dt, .1);
        int steps = Math.max(1, (int) Math.ceil(elapsed * 240));
        double h = elapsed / steps;
        boolean hit = false;
        for (int i = 0; i < steps; i++) {
            double oldVx = vx;
            if (grounded) {
                double loss = FLOOR_FRICTION * h;
                vx = Math.copySign(Math.max(0, Math.abs(vx) - loss), vx);
                // 摩擦减速不反向；停止的最后一步只移动到刹停的位置。
                double movingTime = Math.min(h, Math.abs(oldVx) / FLOOR_FRICTION);
                x += oldVx * movingTime - Math.copySign(.5 * FLOOR_FRICTION * movingTime * movingTime, oldVx);
                y = maxY; vy = 0;
            } else {
                x += vx * h;
                y += vy * h + .5 * GRAVITY * h * h;
                vy += GRAVITY * h;
                double damping = Math.exp(-AIR_DRAG * h);
                vx *= damping; vy *= damping;
            }
            if (x < minX) { x = minX; vx = Math.abs(vx) * .72; hit = true; }
            if (x > maxX) { x = maxX; vx = -Math.abs(vx) * .72; hit = true; }
            if (y < minY) { y = minY; vy = Math.abs(vy) * RESTITUTION; hit = true; }
            if (!grounded && y >= maxY && vy >= 0) {
                y = maxY;
                double impact = vy;
                double rebound = impact * RESTITUTION;
                vx *= .86; // 接触地面的切向能量损失
                hit |= impact >= 90;
                if (rebound < 70) { grounded = true; vy = 0; }
                else { vy = -rebound; }
            }
            if (grounded && Math.abs(vx) < 8) vx = 0;
        }
        return hit;
    }
    boolean stopped() { return grounded && vx == 0 && vy == 0; }
}