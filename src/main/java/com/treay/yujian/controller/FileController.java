package com.treay.yujian.controller;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.SecureUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;

import com.treay.yujian.common.BaseResponse;
import com.treay.yujian.common.ErrorCode;
import com.treay.yujian.common.ResultUtils;
import com.treay.yujian.exception.BusinessException;
import com.treay.yujian.model.domain.Avatar;
import com.treay.yujian.service.AvatarService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.List;

/**
 *  文件上传相关接口
 * @author: Treay
 *
 **/
@RestController
@RequestMapping("/file")
@CrossOrigin(origins = {"http://localhost:3000","http://yujian.treay.cn"},allowCredentials="true")
@Slf4j
public class FileController {

    @Value("${avatar.upload.filePath}")
    private String filePath;

    @Resource
    private AvatarService avatarService;


    /**
     * 文件上传接口
     *
     * @param file
     * @return
     */
    //  接收文件保存到服务器上，然后判断 服务器中是否有相同文件，如果有，则返回文件的url，
    //  如果没有，则构造新的url并返回
    //  最后将该文件相关信息存入数据库
    @PostMapping("/upload")
    public BaseResponse<String> upload(@RequestParam MultipartFile file) throws Exception {

        String originalFilename = file.getOriginalFilename();
        String type = FileUtil.extName(originalFilename);
        long size = file.getSize();
        // 定义保存文件的目录
        File avatarParent = new File(filePath);

        if (!avatarParent.exists()) {
            avatarParent.mkdirs();
        }

        // 定义文件的唯一标识符，就是存储的文件名
        String uuid = IdUtil.fastSimpleUUID();
        String fileUuid = uuid + "." + type;
        File avatar = new File(filePath + File.separator + fileUuid);

        try {
            // 将文件保存到指定位置
            file.transferTo(avatar);
            //获取文件的md5
            String md5 = SecureUtil.md5(avatar);
            //查询文件是否存在
            QueryWrapper<Avatar> queryWrapper = new QueryWrapper<>();
            queryWrapper.lambda().eq(Avatar::getMd5, md5);
            // avatarList是uuid加密后与上传的文件相同的文件列表
            List<Avatar> avatarList = avatarService.list(queryWrapper);
            String url = "";
            if (CollectionUtils.isNotEmpty(avatarList)) {
                // 如果有相同文件则只在数据库中写入，并将传入的文件删除
                url = avatarList.get(0).getUrl();
                avatar.delete();
            } else {
                // 服务器
                //  url = "http://yujian-backend.treay.cn/api/file/" + fileUuid;
                // 本地
                url = "http://localhost:8080/api/file/" + fileUuid;
            }

            //存到数据库
            Avatar avatarFile = new Avatar();
            avatarFile.setName(originalFilename);
            avatarFile.setSize(size / 1024);
            avatarFile.setType(type);
            avatarFile.setUrl(url);
            avatarFile.setMd5(md5);
            avatarFile.setUserId(1L); // 没用但没删
            // insert into avatar(name, size, type, url, md5, user_id。。。)
            avatarService.save(avatarFile);
            // 返回文件路径或其他响应
            return ResultUtils.success(url);
        }catch (IOException e) {
            // 处理文件上传过程中的IO异常
            avatar.delete();
            log.error("File upload failed: ", e);
            return ResultUtils.error(ErrorCode.SYSTEM_ERROR,"上传失败");
        }
    }

    /**
     * 文件下载(当使用特定url访问时，会自动调用该方法，在浏览器上展示或下载文件)
     * @param fileUUID
     * @param response
     * @throws IOException
     */
    @GetMapping("/{fileUUID}")
    public void down(@PathVariable String fileUUID, HttpServletResponse response) throws IOException {
        // 获取要下载的文件
        File file = new File(filePath + "\\" + fileUUID);
        // 构造响应
        response.addHeader("Content-Disposition", "attachment;filename" + URLEncoder.encode(fileUUID, "UTF-8"));
        response.setContentType("application/octet-stream");
        ServletOutputStream os = response.getOutputStream();
        try {
            // 读取文件内容并写入响应
            os.write(jodd.io.FileUtil.readBytes(file));
        }
        catch (IOException e){
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        } finally {
            os.flush();
            os.close();
        }
    }
}
