package br.com.kraftcity.military.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Matrix3f;
import com.mojang.math.Matrix4f;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.npc.Villager;

/** Records the matrices that emitted the visible leg geometry in the current render. */
public final class SoldierLegPoseCapture {
    private static final ThreadLocal<Deque<SoldierLegPoseCapture>> FRAMES = new ThreadLocal<>();

    private final Villager villager;
    private final ModelPart root;
    private boolean resolved;
    private FreshVillagerParts.Anchor left, right;
    private Matrices leftPose, rightPose;

    private record Matrices(Matrix4f pose, Matrix3f normal) {
        Matrices(PoseStack.Pose source) {
            this(source.pose().copy(), source.normal().copy());
        }
    }

    private SoldierLegPoseCapture(Villager villager, ModelPart root) {
        this.villager = villager;
        this.root = root;
    }

    public static void begin(Villager villager, ModelPart root) {
        var frames = FRAMES.get();
        if (frames == null) {
            frames = new ArrayDeque<>();
            FRAMES.set(frames);
        }
        frames.push(new SoldierLegPoseCapture(villager, root));
    }

    public static void end(Villager villager) {
        var frames = FRAMES.get();
        if (frames == null || frames.peek().villager != villager) return;
        frames.pop();
        if (frames.isEmpty()) FRAMES.remove();
    }

    public static void record(ModelPart.Cube cube, PoseStack.Pose pose) {
        var frames = FRAMES.get();
        if (frames == null) return;
        var frame = frames.peek();
        // Resolve after model providers have selected and prepared this frame's geometry.
        if (!frame.resolved) {
            frame.resolved = true;
            frame.left = FreshVillagerParts.leg(frame.root, "left_leg");
            frame.right = FreshVillagerParts.leg(frame.root, "right_leg");
        }
        if (frame.left != null && frame.left.geometry() == cube && frame.leftPose == null)
            frame.leftPose = new Matrices(pose);
        if (frame.right != null && frame.right.geometry() == cube && frame.rightPose == null)
            frame.rightPose = new Matrices(pose);
    }

    static boolean apply(Villager villager, boolean left, PoseStack poses) {
        var frames = FRAMES.get();
        if (frames == null || frames.peek().villager != villager) return false;
        var frame = frames.peek();
        var matrix = left ? frame.leftPose : frame.rightPose;
        if (matrix == null) return false;
        poses.last().pose().load(matrix.pose());
        poses.last().normal().load(matrix.normal());
        (left ? frame.left : frame.right).alignGeometry(poses, false);
        return true;
    }
}
