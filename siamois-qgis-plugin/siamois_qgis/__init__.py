def classFactory(iface):
    from .plugin import SiamoisPlugin
    return SiamoisPlugin(iface)
