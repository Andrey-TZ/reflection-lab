package org.example.classes;

import org.example.generator.Generatable;

@Generatable(implementations = {Triangle.class})
public interface ShapeNew extends Shape{
}
