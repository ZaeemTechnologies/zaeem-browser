package com.zaeem.browser.engine

import com.zaeem.browser.core.FingerprintLevel

/** Document-start scripts. These reduce, not eliminate, fingerprinting; see docs/PRIVACY.md. */
object Scripts {
    fun build(level: FingerprintLevel, blockWebRtc: Boolean, seed: Int): String {
        val sb = StringBuilder("(function(){'use strict';try{\n")
        // Global Privacy Control signal
        sb.append("Object.defineProperty(Navigator.prototype,'globalPrivacyControl',{get:function(){return true},configurable:true});\n")
        if (blockWebRtc) sb.append(
            "['RTCPeerConnection','webkitRTCPeerConnection','RTCDataChannel','RTCSessionDescription'].forEach(function(n){" +
                "try{Object.defineProperty(window,n,{value:undefined,configurable:true})}catch(e){}});\n"
        )
        if (level != FingerprintLevel.OFF) {
            sb.append("var S=$seed|0;function rnd(){S=(S*1664525+1013904223)|0;return ((S>>>8)&255)}\n")
            // Canvas read-back noise (per page-load seed): changes least-significant colour bits only.
            sb.append(
                "var gid=CanvasRenderingContext2D.prototype.getImageData;" +
                    "CanvasRenderingContext2D.prototype.getImageData=function(){var d=gid.apply(this,arguments);" +
                    "for(var i=0;i<d.data.length;i+=4){if((rnd()&7)===0){d.data[i]^=1}}return d};\n" +
                    "var tdu=HTMLCanvasElement.prototype.toDataURL;HTMLCanvasElement.prototype.toDataURL=function(){" +
                    "try{var c=this.getContext('2d');if(c&&this.width*this.height<4000000){var d=gid.call(c,0,0,this.width,this.height);" +
                    "for(var i=0;i<d.data.length;i+=4){if((rnd()&7)===0){d.data[i]^=1}}c.putImageData(d,0,0)}}catch(e){}return tdu.apply(this,arguments)};\n"
            )
            sb.append("function def(o,k,v){try{Object.defineProperty(o,k,{get:function(){return v},configurable:true})}catch(e){}}\n")
            sb.append("def(Navigator.prototype,'hardwareConcurrency',${if (level == FingerprintLevel.STRICT) 2 else 4});\n")
            sb.append("def(Navigator.prototype,'deviceMemory',${if (level == FingerprintLevel.STRICT) 4 else 8});\n")
            if (level == FingerprintLevel.STRICT) {
                sb.append("def(Navigator.prototype,'languages',['en-US','en']);def(Navigator.prototype,'language','en-US');\n")
                sb.append("def(Navigator.prototype,'plugins',[]);def(Navigator.prototype,'mimeTypes',[]);\n")
                // Generic WebGL vendor/renderer strings
                sb.append(
                    "[window.WebGLRenderingContext,window.WebGL2RenderingContext].forEach(function(C){if(!C)return;" +
                        "var gp=C.prototype.getParameter;C.prototype.getParameter=function(p){" +
                        "if(p===37445)return 'Google Inc.';if(p===37446)return 'ANGLE (Generic Renderer)';return gp.apply(this,arguments)}});\n"
                )
                // Audio fingerprint noise
                sb.append(
                    "if(window.AnalyserNode){var gf=AnalyserNode.prototype.getFloatFrequencyData;" +
                        "AnalyserNode.prototype.getFloatFrequencyData=function(a){gf.call(this,a);for(var i=0;i<a.length;i++){a[i]+=(rnd()-128)*1e-7}}}\n"
                )
            }
        }
        sb.append("}catch(e){}})();")
        return sb.toString()
    }
}
