(function(){
var D=["01110 10001 10011 10101 11001 10001 01110","00100 01100 00100 00100 00100 00100 01110","01110 10001 00001 00010 00100 01000 11111","11110 00001 00001 01110 00001 00001 11110","00010 00110 01010 10010 11111 00010 00010","11111 10000 11110 00001 00001 10001 01110","00110 01000 10000 11110 10001 10001 01110","11111 00001 00010 00100 01000 01000 01000","01110 10001 10001 01110 10001 10001 01110","01110 10001 10001 01111 00001 00010 01100"].map(function(s){return s.split(" ")});
var svg=document.getElementById("clock"),NS="http://www.w3.org/2000/svg",shown="";
function dot(x,y,c){var e=document.createElementNS(NS,"circle");e.setAttribute("cx",x+2.5);e.setAttribute("cy",y+2.5);e.setAttribute("r",2.5);if(c)e.setAttribute("class",c);svg.appendChild(e)}
function draw(){
var d=new Date(),t=("0"+d.getHours()).slice(-2)+("0"+d.getMinutes()).slice(-2);
if(t===shown)return;shown=t;while(svg.firstChild)svg.removeChild(svg.firstChild);
var x=0;
for(var i=0;i<4;i++){
if(i===2){dot(x,18,"sep");dot(x,36,"sep");x+=5+14}
var m=D[+t[i]];
for(var r=0;r<7;r++)for(var c=0;c<5;c++)if(m[r].charAt(c)==="1")dot(x+c*9,r*9);
x+=41+14}
svg.setAttribute("aria-label",t.slice(0,2)+":"+t.slice(2))}
if(svg){draw();setInterval(draw,1000)}
window.addEventListener("load",function(){
var sec=document.getElementById("capturas");
if(sec&&!document.querySelector(".shots figure:not([hidden])"))sec.hidden=true});
var API="https://api.github.com/repos/27Salex/usos-launcher/releases/latest";
if(window.fetch)fetch(API,{headers:{Accept:"application/vnd.github+json"}}).then(function(r){if(!r.ok)throw 0;return r.json()}).then(function(j){
var a=(j.assets||[]).filter(function(x){return /\.apk$/i.test(x.name)})[0];
if(!a)return;
document.querySelectorAll("[data-apk]").forEach(function(l){l.href=a.browser_download_url});
var mb=(a.size/1048576).toFixed(1);if(document.documentElement.lang==="es")mb=mb.replace(".",",");
var m=document.getElementById("meta");if(m)m.textContent=(j.tag_name||"")+" · "+mb+" MB · APK"
}).catch(function(){})
})();
