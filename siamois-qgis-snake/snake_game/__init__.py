def classFactory(iface):
    from .plugin import SnakePlugin
    return SnakePlugin(iface)
