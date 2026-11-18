package com.kuangsanfeng.ultimategarden.client.function.path_finding.planning;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static java.lang.foreign.ValueLayout.*;

/** OpenCL 1.2 bindings for one worker-owned command queue. */
final class OpenClCompute implements AutoCloseable {
    private static final MemorySegment NIL=MemorySegment.NULL;
    private static final long GPU=4;
    private final Arena arena=Arena.ofConfined();
    private final Map<String,MethodHandle> api=new HashMap<>();
    private final List<MemorySegment> buffers=new ArrayList<>();
    private MemorySegment context=NIL,queue=NIL,program=NIL,kernel=NIL;
    private MemorySegment integer=arena.allocate(JAVA_INT), pointer=arena.allocate(ADDRESS), size=arena.allocate(JAVA_LONG);
    private String deviceName;

    OpenClCompute(String source){
        try{
            var os=System.getProperty("os.name","").toLowerCase(Locale.ROOT);
            if(os.contains("mac")) throw new IllegalStateException("OPENCL_PLATFORM_UNSUPPORTED");
            var symbols=SymbolLookup.libraryLookup(os.contains("win")?"OpenCL.dll":"libOpenCL.so.1",arena);
            bind(symbols,"GetPlatformIDs",JAVA_INT,JAVA_INT,ADDRESS,ADDRESS);
            bind(symbols,"GetDeviceIDs",JAVA_INT,ADDRESS,JAVA_LONG,JAVA_INT,ADDRESS,ADDRESS);
            bind(symbols,"GetDeviceInfo",JAVA_INT,ADDRESS,JAVA_INT,JAVA_LONG,ADDRESS,ADDRESS);
            bind(symbols,"CreateContext",ADDRESS,ADDRESS,JAVA_INT,ADDRESS,ADDRESS,ADDRESS,ADDRESS);
            bind(symbols,"CreateCommandQueue",ADDRESS,ADDRESS,ADDRESS,JAVA_LONG,ADDRESS);
            bind(symbols,"CreateProgramWithSource",ADDRESS,ADDRESS,JAVA_INT,ADDRESS,ADDRESS,ADDRESS);
            bind(symbols,"BuildProgram",JAVA_INT,ADDRESS,JAVA_INT,ADDRESS,ADDRESS,ADDRESS,ADDRESS);
            bind(symbols,"GetProgramBuildInfo",JAVA_INT,ADDRESS,ADDRESS,JAVA_INT,JAVA_LONG,ADDRESS,ADDRESS);
            bind(symbols,"CreateKernel",ADDRESS,ADDRESS,ADDRESS,ADDRESS);
            bind(symbols,"CreateBuffer",ADDRESS,ADDRESS,JAVA_LONG,JAVA_LONG,ADDRESS,ADDRESS);
            bind(symbols,"SetKernelArg",JAVA_INT,ADDRESS,JAVA_INT,JAVA_LONG,ADDRESS);
            for(var name:List.of("EnqueueWriteBuffer","EnqueueReadBuffer"))
                bind(symbols,name,JAVA_INT,ADDRESS,ADDRESS,JAVA_INT,JAVA_LONG,JAVA_LONG,ADDRESS,JAVA_INT,ADDRESS,ADDRESS);
            bind(symbols,"EnqueueNDRangeKernel",JAVA_INT,ADDRESS,ADDRESS,JAVA_INT,ADDRESS,ADDRESS,ADDRESS,JAVA_INT,ADDRESS,ADDRESS);
            for(var name:List.of("ReleaseMemObject","ReleaseKernel","ReleaseProgram","ReleaseCommandQueue","ReleaseContext"))
                bind(symbols,name,JAVA_INT,ADDRESS);

            MemorySegment device=pickDevice();
            deviceName=deviceInfo(device,0x102B,512).getString(0);
            pointer.set(ADDRESS,0,device);
            context=create("CreateContext",NIL,1,pointer,NIL,NIL,integer);
            queue=create("CreateCommandQueue",context,device,0L,integer);
            pointer.set(ADDRESS,0,arena.allocateFrom(source));
            program=create("CreateProgramWithSource",context,1,pointer,NIL,integer);
            pointer.set(ADDRESS,0,device);
            int compiled=(int)call("BuildProgram",program,1,pointer,NIL,NIL,NIL);
            if(compiled!=0){
                var log=arena.allocate(8192);
                call("GetProgramBuildInfo",program,device,0x1183,log.byteSize(),log,NIL);
                throw new IllegalStateException("OPENCL_KERNEL_BUILD_"+compiled+": "+log.getString(0,StandardCharsets.UTF_8));
            }
            kernel=create("CreateKernel",program,arena.allocateFrom("check_cast"),integer);
        }catch(RuntimeException failure){
            close();
            throw failure;
        }
    }
    private MemorySegment pickDevice(){
        checked("GetPlatformIDs",0,NIL,integer);
        int platforms=integer.get(JAVA_INT,0);
        if(platforms<=0)throw new IllegalStateException("OPENCL_NO_PLATFORM");
        var list=arena.allocate(ADDRESS,platforms);
        checked("GetPlatformIDs",platforms,list,NIL);
        MemorySegment chosen=NIL;
        long rank=-1;
        for(int i=0;i<platforms;i++){
            var platform=list.getAtIndex(ADDRESS,i);
            if((int)call("GetDeviceIDs",platform,GPU,0,NIL,integer)!=0)continue;
            int count=integer.get(JAVA_INT,0);
            var devices=arena.allocate(ADDRESS,count);
            checked("GetDeviceIDs",platform,GPU,count,devices,NIL);
            for(int j=0;j<count;j++){
                var device=devices.getAtIndex(ADDRESS,j);
                if(deviceInfo(device,0x1032,8).get(JAVA_LONG,0)==0)continue;
                boolean discrete=deviceInfo(device,0x1035,4).get(JAVA_INT,0)==0;
                long score=(discrete?1_000_000:0)+deviceInfo(device,0x1002,4).get(JAVA_INT,0);
                if(score>rank){chosen=device;rank=score;}
            }
        }
        if(chosen.equals(NIL))throw new IllegalStateException("OPENCL_NO_GPU_WITH_FP64");
        return chosen;
    }
    private MemorySegment deviceInfo(MemorySegment device,int field,long bytes){
        var data=arena.allocate(bytes,8);
        checked("GetDeviceInfo",device,field,bytes,data,NIL);
        return data;
    }
    private void bind(SymbolLookup lookup,String name,MemoryLayout result,MemoryLayout... args){
        var address=lookup.find("cl"+name).orElseThrow(()->new IllegalStateException("OPENCL_SYMBOL_"+name));
        api.put(name,Linker.nativeLinker().downcallHandle(address,FunctionDescriptor.of(result,args)));
    }
    private Object call(String name,Object... args){
        try{return api.get(name).invokeWithArguments(args);}
        catch(Throwable failure){throw new IllegalStateException("OPENCL_"+name,failure);}
    }
    private void checked(String name,Object... args){
        int error=(int)call(name,args);
        if(error!=0)throw new IllegalStateException("OPENCL_"+name+"_"+error);
    }
    private MemorySegment create(String name,Object... args){
        var result=(MemorySegment)call(name,args);
        int error=integer.get(JAVA_INT,0);
        if(error!=0||result.equals(NIL))throw new IllegalStateException("OPENCL_"+name+"_"+error);
        return result;
    }
    String deviceName(){return deviceName;}
    MemorySegment host(long bytes){return arena.allocate(bytes,8);}
    MemorySegment buffer(long bytes){
        var result=create("CreateBuffer",context,1L,bytes,NIL,integer);
        buffers.add(result);
        return result;
    }
    void arg(int index,MemorySegment buffer){
        pointer.set(ADDRESS,0,buffer);
        checked("SetKernelArg",kernel,index,ADDRESS.byteSize(),pointer);
    }
    void write(MemorySegment device,long offset,MemorySegment host,long bytes){
        checked("EnqueueWriteBuffer",queue,device,1,offset,bytes,host,0,NIL,NIL);
    }
    void launch(int count){
        size.set(JAVA_LONG,0,count);
        checked("EnqueueNDRangeKernel",queue,kernel,1,NIL,size,NIL,0,NIL,NIL);
    }
    void read(MemorySegment device,MemorySegment host,long bytes){
        checked("EnqueueReadBuffer",queue,device,1,0L,bytes,host,0,NIL,NIL);
    }
    private void release(String name,MemorySegment object){
        if(!object.equals(NIL))call(name,object);
    }
    @Override public void close(){
        try{
            for(var buffer:buffers)release("ReleaseMemObject",buffer);
            release("ReleaseKernel",kernel);
            release("ReleaseProgram",program);
            release("ReleaseCommandQueue",queue);
            release("ReleaseContext",context);
        }finally{arena.close();}
    }
}
