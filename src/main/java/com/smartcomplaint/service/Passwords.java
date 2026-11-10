
package com.smartcomplaint.service;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

public final class Passwords {

    private Passwords(){}

    public static String hash(String password) throws Exception {
        byte[] salt=new byte[16];
        new SecureRandom().nextBytes(salt);
        byte[] key=derive(password,salt);
        return "pbkdf2$"+Base64.getEncoder().encodeToString(salt)+"$"+Base64.getEncoder().encodeToString(key);
    }

    public static boolean verify(String password,String stored) throws Exception {
        if(stored==null)return false;
        String[] p=stored.split("\\$");
        if(p.length!=3||!p[0].equals("pbkdf2"))return false;
        byte[] salt=Base64.getDecoder().decode(p[1]);
        byte[] expected=Base64.getDecoder().decode(p[2]);
        return java.security.MessageDigest.isEqual(expected,derive(password,salt));
    }

    private static byte[] derive(String p,byte[] s)throws Exception {
        PBEKeySpec spec=new PBEKeySpec(p.toCharArray(),s,120000,256);
        try{
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        }finally{
            spec.clearPassword();
        }
    }
}
