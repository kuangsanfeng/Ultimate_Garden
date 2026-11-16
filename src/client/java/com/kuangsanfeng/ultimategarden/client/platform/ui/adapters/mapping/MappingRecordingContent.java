package com.kuangsanfeng.ultimategarden.client.platform.ui.adapters.mapping;

import com.kuangsanfeng.ultimategarden.client.function.mapping.*;
import com.kuangsanfeng.ultimategarden.client.platform.ui.language.LanguageManager;

public final class MappingRecordingContent {
    private MappingRecordingContent(){}
    public static String value(String key,MappingRecordingSnapshot s,LanguageManager l){
        if(s.worldName()==null||s.worldName().isBlank())return "\u2014";
        if(key.equals("recording_status")){
            if(s.config()==null)return l.text("ui.ultimategarden.mapping_hud.not_initialized");
            if(!s.recording())return l.text("ui.ultimategarden.mapping_hud.stopped");
            if(s.processing().isEmpty()&&s.queued().isEmpty())return l.text("ui.ultimategarden.mapping_hud.waiting");
            String text=l.text("ui.ultimategarden.mapping_hud.processing_count",s.processing().size());return s.queued().isEmpty()?text:text+l.text("ui.ultimategarden.mapping_hud.queued_count",s.queued().size());
        }
        if(key.equals("recording_layer"))return l.text("ui.ultimategarden.mapping.festival."+(s.config()==null?Festival.NONE:s.config().selectedFestival()).name());
        if(s.config()==null)return "\u2014";
        return switch(key){
            case "recording_origin"->{var o=s.config().origin();yield "X "+o.x()+" \u00b7 Y "+o.y()+" \u00b7 Z "+o.z();}
            case "recording_chunk"->s.current()==null?"\u2014":s.current().x()+" "+s.current().z();
            case "recording_current_state"->s.currentState()==MappingRecordingSnapshot.ChunkState.UNKNOWN?"\u2014":l.text("ui.ultimategarden.mapping_hud.chunk_state."+s.currentState().name());
            case "recording_saved"->l.text("ui.ultimategarden.mapping_hud.saved_count",s.saved().size());
            default->"\u2014";
        };
    }
}
