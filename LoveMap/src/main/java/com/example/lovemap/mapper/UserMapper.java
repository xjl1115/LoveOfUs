package com.example.lovemap.mapper;

import com.example.lovemap.model.entity.User;
import com.example.lovemap.model.vo.UserStatsVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户 Mapper
 */
@Mapper
public interface UserMapper {

    /**
     * 新增用户
     */
    int insert(User user);

    /**
     * 根据邮箱查询用户
     */
    User findByEmail(@Param("email") String email);

    /**
     * 根据手机号查询用户
     */
    User findByPhone(@Param("phone") String phone);

    /**
     * 根据ID查询用户
     */
    User selectById(@Param("id") Integer id);

    /**
     * 更新用户密码
     */
    int updatePassword(User user);

    /**
     * 更新用户信息（昵称、头像、关系开始日期）
     */
    int updateUser(User user);

    /**
     * 绑定伴侣：更新 partner_id、group_id、is_bound
     */
    int updatePartnerBind(@Param("id") Long id,
                          @Param("partnerId") Long partnerId,
                          @Param("groupId") Long groupId);

    /**
     * 查询用户照片总数
     */
    Integer countPhotoByUserId(@Param("userId") Long userId);

    /**
     * 按情侣组查询照片总数
     */
    Integer countPhotoByGroupId(@Param("groupId") Integer groupId);

    /**
     * 查询用户相册总数
     */
    Integer countAlbumByUserId(@Param("userId") Long userId);

    /**
     * 查询用户到过的城市数量
     */
    Integer countCityByUserId(@Param("userId") Long userId);

    /**
     * 按情侣组查询到过的城市数量
     */
    Integer countCityByGroupId(@Param("groupId") Integer groupId);

    /**
     * 查询用户各省份照片数量统计（从 photo 表查询 province，关联 provence 表获取省份名称）
     *
     * @param userId 用户ID
     * @return 省份照片统计数据列表
     */
    List<UserStatsVO.ProvinceStatVO> countProvinceByUserId(@Param("userId") Long userId);

    /**
     * 按情侣组查询各省份照片数量统计
     */
    List<UserStatsVO.ProvinceStatVO> countProvinceByGroupId(@Param("groupId") Integer groupId);

    /**
     * 解除伴侣绑定：清空 partner_id、group_id、is_bound、relationship_start
     */
    int clearPartnerBind(@Param("id") Long id);

    /**
     * 根据情侣组ID查询所有成员
     */
    List<User> selectByGroupId(@Param("groupId") Long groupId);

    /**
     * 软删除用户账号
     */
    int deleteAccount(User user);

    /**
     * 查询所有用户（用于定时任务）
     */
    List<User> selectAll();
}
