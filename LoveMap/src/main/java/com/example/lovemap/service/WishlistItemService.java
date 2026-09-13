package com.example.lovemap.service;

import com.example.lovemap.common.Result;
import com.example.lovemap.model.dto.AiRecommendRequestDTO;
import com.example.lovemap.model.dto.WishlistItemCreateDTO;
import com.example.lovemap.model.dto.WishlistItemStatusDTO;
import com.example.lovemap.model.dto.WishlistItemUpdateDTO;
import com.example.lovemap.model.dto.WishlistProgressDTO;
import com.example.lovemap.model.vo.AiRecommendVO;
import com.example.lovemap.model.vo.WishlistItemVO;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 心愿清单服务接口
 */
public interface WishlistItemService {

    /**
     * 查询当前 group 的心愿清单列表
     */
    Result<List<WishlistItemVO>> listWishlistItems(Integer userId);

    /**
     * 查询心愿项详情
     */
    Result<WishlistItemVO> getWishlistItemDetail(Integer userId, Long id);

    /**
     * 创建心愿项
     */
    Result<WishlistItemVO> createWishlistItem(Integer userId, WishlistItemCreateDTO dto);

    /**
     * 更新心愿项
     */
    Result<WishlistItemVO> updateWishlistItem(Integer userId, Long id, WishlistItemUpdateDTO dto);

    /**
     * 变更心愿项状态
     */
    Result<WishlistItemVO> updateWishlistItemStatus(Integer userId, Long id, WishlistItemStatusDTO dto);

    /**
     * 调整心愿进度（原子增减，自动限制在 [0, targetValue]）
     */
    Result<WishlistItemVO> adjustWishlistItemProgress(Integer userId, Long id, WishlistProgressDTO dto);

    /**
     * 上传达成纪念照片（1 张，替换时清理旧文件）
     */
    Result<WishlistItemVO> uploadWishlistItemPhoto(Integer userId, Long id, MultipartFile file);

    /**
     * 读取达成纪念照片字节（服务端转发，供前端以同域资源展示与导出卡片）
     */
    ResponseEntity<Resource> loadWishlistItemPhoto(Integer userId, Long id);

    /**
     * 删除心愿项
     */
    Result<Void> deleteWishlistItem(Integer userId, Long id);

    /**
     * AI 推荐心愿清单
     */
    Result<AiRecommendVO> recommendWishlistItems(Integer userId, AiRecommendRequestDTO dto);
}
