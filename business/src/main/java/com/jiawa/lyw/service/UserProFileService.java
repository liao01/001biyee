package com.jiawa.lyw.service;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import com.jiawa.lyw.context.LoginMemberContext;
import com.jiawa.lyw.domain.Member;
import com.jiawa.lyw.domain.MemberExample;
import com.jiawa.lyw.domain.UserProfile;
import com.jiawa.lyw.domain.UserProfileExample;
import com.jiawa.lyw.enums.UserProFileEnum;
import com.jiawa.lyw.exception.BusinessException;
import com.jiawa.lyw.exception.BusinessExceptionEnum;
import com.jiawa.lyw.mapper.MemberMapper;
import com.jiawa.lyw.mapper.UserProfileMapper;
import com.jiawa.lyw.mapper.UserProfileMapperCust;
import com.jiawa.lyw.req.UserProfileReq;
import com.jiawa.lyw.resp.UserProfileResp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URL;
import java.util.List;

@Service
@Slf4j
public class UserProFileService {

    @Autowired
    private UserProfileMapper userProfileMapper;

    @Autowired
    private UserProfileMapperCust userProfileMapperCust;

    @Autowired
    private MemberMapper memberMapper;

    private static final String UPLOAD_DIR = "D:/idea/lyw/uploads/avatar/";

    /**
     * 通过 UserProfilereq 新增用户资料
     */
    @Transactional
    public void insertUserProfile(UserProfileReq req) {
        Long userId = LoginMemberContext.getId();
        log.info("保存用户资料开始, userId={}", userId);

        UserProfileExample example = new UserProfileExample();
        example.createCriteria().andUserIdEqualTo(userId);
        List<UserProfile> existingProfiles = userProfileMapper.selectByExample(example);

        UserProfile userProfile = new UserProfile();
        userProfile.setUserId(userId);

        // 头像处理
        if (req.getAvatar() != null && req.getAvatar().startsWith("data:image")) {
            String avatarUrl = uploadUserAvatar(userId, req.getAvatar());
            userProfile.setAvatar(avatarUrl);
        } else {
            userProfile.setAvatar(req.getAvatar()); // URL 或 null
        }

        // 性别处理（带安全校验）
        UserProFileEnum genderEnum = UserProFileEnum.fromCode(String.valueOf(req.getGender()));
        if (genderEnum == null) {
            throw new BusinessException(BusinessExceptionEnum.INVALID_GENDER);
        }
        userProfile.setGender(Byte.valueOf(genderEnum.getCode()));

        userProfile.setBio(req.getBio());
        userProfile.setBirthday(req.getBirthday());
        userProfile.setLocation(req.getLocation());


        MemberExample memberExample = new MemberExample();
        memberExample.createCriteria().andIdEqualTo(userId);
        Member member = new Member();
        member.setName(req.getUsername());
        memberMapper.updateByExampleSelective(member, memberExample);
        log.info("已同步更新 Member 用户名, userId={}", userId);

        if (existingProfiles.isEmpty()) {
            userProfile.setId(IdUtil.getSnowflakeNextId());
            userProfileMapper.insert(userProfile);
            log.info("新增用户资料成功, userId={}", userId);
        } else {
            userProfileMapper.updateByExampleSelective(userProfile, example);
            log.info("更新用户资料成功, userId={}", userId);
        }
    }

    /**
     * 上传用户头像
     */
    public String uploadUserAvatar(Long userId, String avatarData) {
        log.info("上传用户头像开始: userId={}", userId);

        if (avatarData == null || avatarData.isEmpty()) {
            throw new BusinessException(BusinessExceptionEnum.IMAGE_NOT_ERROR);
        }

        String newFileName = userId + "_" + java.util.UUID.randomUUID().toString().replace("-", "");

        try {
            if (avatarData.startsWith("data:image")) {
                // Base64 图片
                String[] parts = avatarData.split(",");
                byte[] data = java.util.Base64.getDecoder().decode(parts[1]);

                String suffix = ".png";
                if (parts[0].contains("jpeg")) suffix = ".jpg";
                else if (parts[0].contains("gif")) suffix = ".gif";

                newFileName += suffix;
                FileUtil.writeBytes(data, UPLOAD_DIR + newFileName);
            } else {
                // URL 下载
                String suffix = avatarData.contains(".") ? avatarData.substring(avatarData.lastIndexOf(".")) : "";
                newFileName += suffix;
                FileUtil.writeFromStream(new URL(avatarData).openStream(), UPLOAD_DIR + newFileName);
            }

            log.info("用户头像上传成功: {}", newFileName);
            return "/uploads/avatar/" + newFileName;

        } catch (IOException e) {
            log.error("头像处理失败: {}", avatarData, e);
            throw new BusinessException(BusinessExceptionEnum.IMAGE_NOT_ERROR);
        }
    }
    /**
     * 根据当前用户查找头像
     * @return
     */
    public String findAvatarUser(Long id){
       return userProfileMapperCust.selectAvatarById(id);
    }

    /**
     * 根据当前用户查找头像
     * @return
     */
    public List<UserProfileResp> findAllFileUser(Long userId){
        List<UserProfileResp> allProfile = userProfileMapperCust.selectUserProfile(userId);
        return allProfile;

    }
}
