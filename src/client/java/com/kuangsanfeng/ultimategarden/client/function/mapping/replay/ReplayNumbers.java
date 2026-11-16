package com.kuangsanfeng.ultimategarden.client.function.mapping.replay;

import java.math.BigInteger;

/** Editable text is committed explicitly; chunk limits have no arbitrary business ceiling. */
public final class ReplayNumbers {
    private ReplayNumbers(){}
    public static BigInteger commit(String draft,String last,BigInteger min,BigInteger max){
        BigInteger value;try{value=new BigInteger(draft.strip());}catch(RuntimeException e){value=new BigInteger(last);}
        value=value.max(min);return max==null?value:value.min(max);
    }
    public static BigInteger wheel(String draft,String last,BigInteger min,BigInteger max,double steps){return commit(commit(draft,last,min,max).add(BigInteger.valueOf((long)Math.signum(steps))).toString(),last,min,max);}
}
