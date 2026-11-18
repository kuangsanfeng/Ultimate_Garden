package com.kuangsanfeng.ultimategarden.client.function.path_finding.planning;

final class GpuKernel {
    private GpuKernel(){}
    static final String SOURCE="""
        #pragma OPENCL EXTENSION cl_khr_fp64 : enable
        #pragma OPENCL FP_CONTRACT OFF
        int contains(double3 p,double3 lo,double3 hi) {
            return all(p>=lo)&&all(p<hi);
        }
        int hit(double3 a,double3 b,double3 lo,double3 hi) {
            if(contains(a,lo,hi)||contains(b,lo,hi))return 1;
            double3 d=b-a;
            for(int i=0;i<3;i++){
                if(fabs(d[i])<=0.0000001)continue;
                double t=((d[i]>0?lo[i]:hi[i])-a[i])/d[i];
                if(t<=0||t>=1)continue;
                double3 q=a+d*t;
                int j=(i+1)%3,k=(i+2)%3;
                if(q[j]>lo[j]-0.0000001&&q[j]<hi[j]+0.0000001&&
                   q[k]>lo[k]-0.0000001&&q[k]<hi[k]+0.0000001)return 1;
            }
            return 0;
        }
        int test(global const int* meta,global const int* cells,global const double* boxes,
                 double3 a,double3 b,int sweep){
            double3 low=fmin(a,b)+(double3)(-0.2999,0.0001,-0.2999);
            double3 high=fmax(a,b)+(double3)(0.2999,1.7999,0.2999);
            int3 minBlock=convert_int3_rtn(low),maxBlock=convert_int3_rtn(high);
            int minY=meta[3], height=meta[4];
            if(minBlock.y<minY||maxBlock.y>=minY+height)return 0;
            for(int x=minBlock.x;x<=maxBlock.x;x++)for(int z=minBlock.z;z<=maxBlock.z;z++){
                int cx=x>>4,cz=z>>4,dx=cx-meta[0],dz=cz-meta[1];
                if(dx<0||dx>=meta[2]||dz<0||dz>=meta[5])return 0;
                int slot=meta[8+dz*meta[2]+dx];
                if(slot<0)return 0;
                int column=slot*256*height+(z&15)*16+(x&15);
                for(int y=minBlock.y;y<=maxBlock.y;y++){
                    int index=cells[column+(y-minY)*256];
                    if(index<0)return 0;
                    int count=index&255, offset=index>>8;
                    for(int i=0;i<count;i++){
                        global const double* box=boxes+(offset+i)*6;
                        double3 block=(double3)(x,y,z);
                        double3 lo=vload3(0,box)+block,hi=vload3(1,box)+block;
                        if(sweep){
                            if(hit(a,b,lo-(double3)(0.2999,1.7999,0.2999),
                                    hi+(double3)(0.2999,-0.0001,0.2999)))return 0;
                        }else if(all(hi>low)&&all(lo<high))return 0;
                    }
                }
            }
            return 1;
        }
        kernel void check_cast(global const int* input,global const int* cells,
                               global const double* boxes,global int* result){
            int i=get_global_id(0);
            global const double* ray=(global const double*)(input+64)+i*9;
            double3 a=vload3(0,ray), b=vload3(1,ray), c=vload3(2,ray);
            result[i]=test(input,cells,boxes,c,c,0)&&test(input,cells,boxes,a,b,1)&&test(input,cells,boxes,b,c,1);
        }
        """;
}
