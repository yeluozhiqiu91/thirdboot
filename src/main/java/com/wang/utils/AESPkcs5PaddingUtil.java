package com.wang.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;

/**
 * @Author: wangliujie
 * @Date: 2019/07/05 15:15
 */
public class AESPkcs5PaddingUtil {
    private static final Logger logger = LoggerFactory.getLogger(AESPkcs5PaddingUtil.class);
    // AES/CBC/PKCS7Padding
    private final String CIPHERMODEPADDING = "AES/CBC/PKCS5Padding";
    private static Map<String,SecretKeySpec> skforAESMap = new HashMap<>(2);
    private static Map<String,IvParameterSpec> ivMap = new HashMap<>(2);
    private static Map<String,AESPkcs5PaddingUtil> instanceMap = new HashMap<>(2);
    private String secretStr;
    private String offsetStr;

    public static AESPkcs5PaddingUtil getInstance(String secretKey, String offset) {
        String mapKey = secretKey+"_"+offset;
        if (instanceMap == null||instanceMap.get(mapKey)==null) {
            synchronized (AESPkcs5PaddingUtil.class) {
                if (instanceMap == null||instanceMap.get(mapKey)==null) {
                    instanceMap.put(mapKey,new AESPkcs5PaddingUtil(secretKey, offset));
                }
            }
        }
        return instanceMap.get(mapKey);
    }

    /**
     * 多例模式，通过密钥_偏移量区分不同实例
     * @param secretKey 密钥
     * @param offset 偏移量
     */
    private AESPkcs5PaddingUtil(String secretKey, String offset) {
        byte[] skAsByteArray;
        try {
            skAsByteArray = secretKey.getBytes("utf-8");
            SecretKeySpec temp = new SecretKeySpec(skAsByteArray, "AES");
            skforAESMap.put(secretKey+"_"+offset,temp);
        } catch (UnsupportedEncodingException e) {
            logger.error("不支持的编码",e);
        }
        ivMap.put(secretKey+"_"+offset,new IvParameterSpec(offset.getBytes()));
        secretStr = secretKey;
        offsetStr = offset;
    }

    public String encrypt(byte[] plaintext) {
        String mapKey = this.secretStr+"_"+this.offsetStr;
        byte[] ciphertext = encrypt(CIPHERMODEPADDING, skforAESMap.get(mapKey), ivMap.get(mapKey), plaintext);
        String base64_ciphertext = Base64Encoder.encode(ciphertext);
        return base64_ciphertext;
    }

    public String decrypt(String ciphertext_base64) {
        String mapKey = this.secretStr+"_"+this.offsetStr;
        byte[] s = Base64Decoder.decodeToBytes(ciphertext_base64);
        String decrypted = null;
        try {
            decrypted = new String(decrypt(CIPHERMODEPADDING, skforAESMap.get(mapKey), ivMap.get(mapKey), s),"utf-8");
        } catch (UnsupportedEncodingException e) {
            logger.error("解密异常，不支持的字符编码",e);
        }
        return decrypted;
    }

    /**
     * 加密
     * @param cmp 加密及填充模式
     * @param sk 加密密钥
     * @param IV 偏移量
     * @param msg 待加密内容字节
     * @return
     */
    private byte[] encrypt(String cmp, SecretKey sk, IvParameterSpec IV, byte[] msg) {
        try {
            Cipher c = Cipher.getInstance(cmp);
            c.init(Cipher.ENCRYPT_MODE, sk, IV);
            return c.doFinal(msg);
        } catch (Exception e) {
            logger.error("加密异常",e);
        }
        return null;
    }

    /**
     * 解密
     * @param cmp 加密及填充模式
     * @param sk 加密密钥
     * @param IV 偏移量
     * @param ciphertext 密文内容字节
     * @return
     */
    private byte[] decrypt(String cmp, SecretKey sk, IvParameterSpec IV, byte[] ciphertext) {
        try {
            Cipher c = Cipher.getInstance(cmp);
            c.init(Cipher.DECRYPT_MODE, sk, IV);
            return c.doFinal(ciphertext);
        } catch (Exception e) {
            logger.error("解密异常",e);
        }
        return null;
    }

    public String getSecretStr() {
        return secretStr;
    }

    public void setSecretStr(String secretStr) {
        this.secretStr = secretStr;
    }

    public String getOffsetStr() {
        return offsetStr;
    }

    public void setOffsetStr(String offsetStr) {
        this.offsetStr = offsetStr;
    }
    /*public static void main(String[] args) throws UnsupportedEncodingException {
        String str = "1234567abcdefg";
        System.out.println(str);
        String str1 = AESPkcs5PaddingUtil.getInstance("1234567890abcdef","1234567890abcdef").encrypt(str.getBytes("utf-8"));
        String mingwen = AESPkcs5PaddingUtil.getInstance("1234567890abcdef","1234567890abcdef").decrypt(str1);
        System.out.println("mingwen:"+mingwen);
    }*/
}
