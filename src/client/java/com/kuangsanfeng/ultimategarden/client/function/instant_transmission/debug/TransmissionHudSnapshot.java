package com.kuangsanfeng.ultimategarden.client.function.instant_transmission.debug;
/** Immutable display values only: no entity, world, plan, input or logger references. */
public record TransmissionHudSnapshot(boolean available,boolean running,String phase,String reason,String lastResult,
 int groupIndex,int totalGroups,int clickGroup,Double testYaw,Double testPitch,Double actualYaw,Double actualPitch,
 boolean reset,Double alignmentYaw,Double alignmentPitch,Double relativeHeight,int preSamples,int dispatched,int planned,int updates,Long relativeTick,String terminationStage) {
 public TransmissionHudSnapshot(boolean available,boolean running,String phase,String reason,String lastResult,
 int groupIndex,int totalGroups,int clickGroup,Double testYaw,Double testPitch,Double actualYaw,Double actualPitch,
 boolean reset,Double alignmentYaw,Double alignmentPitch,Double relativeHeight,int preSamples,int dispatched,int planned,int updates,Long relativeTick){this(available,running,phase,reason,lastResult,groupIndex,totalGroups,clickGroup,testYaw,testPitch,actualYaw,actualPitch,reset,alignmentYaw,alignmentPitch,relativeHeight,preSamples,dispatched,planned,updates,relativeTick,null);}
 public TransmissionHudSnapshot stopped(String why){return stopped(why,why.equals("COMPLETED")?"COMPLETED":why.equals("MANUAL_STOP")?"MANUAL_STOP":"RUNNING_STOP");}
 public TransmissionHudSnapshot stopped(String why,String stage){return new TransmissionHudSnapshot(available,false,phase,why,lastResult,groupIndex,totalGroups,clickGroup,testYaw,testPitch,actualYaw,actualPitch,reset,alignmentYaw,alignmentPitch,relativeHeight,preSamples,dispatched,planned,updates,relativeTick,stage);}
 public static TransmissionHudSnapshot rejected(String why,Double yaw,Double pitch){return new TransmissionHudSnapshot(false,false,"START_VALIDATION",why,null,0,0,0,null,null,yaw,pitch,false,null,null,null,0,0,0,0,null,"START_REJECTED");}
 public static String phase(String state,Long relativeTick,int planned,boolean endpoint,boolean awaitingResponse){
  if(state.equals("TEST_WAIT")){
   if(relativeTick!=null&&relativeTick<2L*(planned-1))return "CLICK_SEQUENCE";
   return endpoint&&!awaitingResponse?"TEST_LANDING":"WAIT_POSITION";
  }
  return state;
 }
}
