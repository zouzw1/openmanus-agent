package com.openmanus.saa.tool.annotation;

import com.openmanus.saa.model.ToolMetadata.Category;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 工具定义注解（扩展 Spring AI 的 @Tool 注解）
 *
 * <p>提供额外的工具元数据，支持分类、标签、危险标记等。
 * 与 Spring AI 的 {@code @Tool} 注解配合使用。
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * @ToolDefinition(
 *     name = "customFileTool",
 *     description = "Custom file operation tool",
 *     category = Category.FILE,
 *     tags = {"safe", "readonly"},
 *     dangerous = false,
 *     requiresConfirm = false,
 *     version = "1.0.0"
 * )
 * public String myToolMethod() {
 *     // ...
 * }
 * }</pre>
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ToolDefinition {

    /**
     * 工具名称，默认使用方法名
     */
    String name() default "";

    /**
     * 工具描述（可从 @Tool.description 继承）
     */
    String description() default "";

    /**
     * 工具分类
     */
    Category category() default Category.OTHER;

    /**
     * 工具标签（如 "safe", "readonly", "destructive" 等）
     */
    String[] tags() default {};

    /**
     * 是否为危险操作（可能造成数据丢失或安全风险）
     */
    boolean dangerous() default false;

    /**
     * 是否需要用户确认后再执行
     */
    boolean requiresConfirm() default false;

    /**
     * 工具版本
     */
    String version() default "1.0.0";

    /**
     * 自定义返回描述
     */
    String returns() default "";
}
