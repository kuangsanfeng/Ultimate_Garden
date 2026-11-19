package com.kuangsanfeng.ultimategarden.client.function.path_finding.execution;

import com.kuangsanfeng.ultimategarden.client.function.instant_transmission.flight.Point;

/** Single-flight server position update. A stopped cast keeps its place until it can be drained. */
public final class PathPositionReceipt {
    private Object connection,world,owner;
    private Point expected;
    private boolean retired;

    public void context(Object currentConnection,Object currentWorld){
        if(connection==currentConnection&&world==currentWorld)return;
        connection=currentConnection;world=currentWorld;owner=null;expected=null;retired=false;
    }
    public boolean pending(){return owner!=null;}
    public boolean retired(){return retired;}
    public void dispatch(Object session,Point landing){
        if(pending())throw new IllegalStateException("PATH_POSITION_RECEIPT_PENDING");
        owner=session;expected=landing;retired=false;
    }
    public void retire(Object session){if(owner==session)retired=true;}
    public Object accept(Point feet){
        if(!pending())return null;
        // A correction at an unrelated position cannot drain a stopped cast.
        if(retired&&feet.distance(expected)>.25)return null;
        Object recipient=retired?null:owner;
        owner=null;expected=null;retired=false;
        return recipient;
    }
}
