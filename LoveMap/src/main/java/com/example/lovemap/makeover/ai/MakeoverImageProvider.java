package com.example.lovemap.makeover.ai;

/**
 * 改造图生成策略接口
 * <p>
 * 后续可替换为 StableDiffusionImageProvider / MeituApiProvider，
 * Service 层只依赖此接口。
 */
public interface MakeoverImageProvider {

    /**
     * 基于原图与编辑提示词，生成改造图字节流
     *
     * @param originalUrl    原图 OSS URL
     * @param editPrompt     妆容/服装编辑描述
     * @param timeoutSeconds 超时上限
     * @return 改造图 PNG 字节
     */
    byte[] edit(String originalUrl, String editPrompt, int timeoutSeconds);
}