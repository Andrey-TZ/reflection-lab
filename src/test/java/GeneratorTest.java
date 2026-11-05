import org.example.classes.*;
import org.example.generator.Generator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.*;

public class GeneratorTest {
    @Test
    void binaryTreeNodeAllowNullable() {
        try {
            Set<BinaryTreeNode> visited = new HashSet<>();
            Deque<BinaryTreeNode> nodes = new LinkedList<>();

            BinaryTreeNode generatedTree = Generator.generate(BinaryTreeNode.class);
            nodes.add(generatedTree);
            Assertions.assertNull(generatedTree.depth);
            generatedTree.depth = 1;

            while (!nodes.isEmpty()) {
                var tree = nodes.pop();
                var left = tree.getLeft();
                var right = tree.getRight();
                if (tree.depth > Generator.getMaxDepth()) {
                    Assertions.assertNull(tree.getLeft());
                    Assertions.assertNull(tree.getRight());
                    Assertions.assertNull(tree.getData());
                } else {
                    Assertions.assertTrue(tree.getData() != null && tree.getData() >= 2 && tree.getData() <= 10,
                            "unexpected value: " + tree.getData());
                }
                if (left != null && !visited.contains(left)) {
                    nodes.add(left);
                    left.depth = tree.depth+1;
                }
                if (right != null && !visited.contains(right)) {
                    nodes.add(right);
                    right.depth = tree.depth+1;
                }
                visited.add(tree);
            }
        } catch (Exception e) {
            Assertions.fail(e.getMessage(), e);
        }
    }

    @Test
    void product_PrimalConstructor() {
        Product generated = Generator.generate(Product.class);
        Assertions.assertEquals(Double.MIN_VALUE, generated.getPrice());
    }

    @Test
    void cart() {
        Cart cart = Generator.generate(Cart.class);
        Assertions.assertEquals(5, cart.getItems().size());
    }

    @Test
    void shape() {
        Shape generated = Generator.generate(Shape.class);
        Assertions.assertTrue(Proxy.isProxyClass(generated.getClass()));
        InvocationHandler handler = Proxy.getInvocationHandler(generated);
        Assertions.assertNotNull(handler);

        double area = generated.getArea();
        double perimeter = generated.getPerimeter();
        Assertions.assertTrue(area > 0);
        Assertions.assertTrue(perimeter > 0);

        Assertions.assertEquals(area, generated.getArea());
        Assertions.assertEquals(perimeter, generated.getPerimeter());
    }

    @Test
    void shapeWithImplementations() {
        ShapeNew generated = Generator.generate(ShapeNew.class);
        Assertions.assertInstanceOf(Triangle.class, generated);
    }

}
